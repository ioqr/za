package za.engine.event;

import za.engine.MessageHandler;
import za.engine.MessageQueue;
import za.engine.event.lib.EventedHttpClient;
import za.engine.event.lib.EventedMessageQueue;
import za.engine.http.DrainableHttpClient;
import za.engine.mq.RabbitMQClient;
import za.lib.HttpClient;
import za.lib.Logger;

import java.util.ConcurrentModificationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EventLoop implements Runnable {
    public static final int MAX_CONCURRENT_EVENTS = 0xffff;
    
    private final Logger log;
    private final ConcurrentLinkedQueue<Event> events;
    private final DrainableHttpClient http;
    private final AsyncMessageQueue asyncMessageQueue;
    private final Consumer<MessageHandler.Props> messageSubscriber;

    // metrics (visible to debugger)
    private final AtomicLong submitCalls = new AtomicLong();
    private final AtomicLong processSingleEventCalls = new AtomicLong();
    private final AtomicLong waitCalls = new AtomicLong();
    private final AtomicLong notifyCalls = new AtomicLong();

    // exported state
    private final EventedHttpClient httpWrapper;
    private final EventedMessageQueue mqWrapper;

    private volatile boolean running = false;

    public EventLoop(
            String rmqReceiveQueueName,
            Supplier<RabbitMQClient> rmqFactory,
            Supplier<DrainableHttpClient> httpFactory,
            Consumer<MessageHandler.Props> messageSubscriber) {
        this.log = Logger.verbose(EventLoop.class);
        this.events = new ConcurrentLinkedQueue<>();
        this.http = httpFactory.get();
        this.httpWrapper = new EventedHttpClient(this);
        this.mqWrapper = new EventedMessageQueue(this);
        this.asyncMessageQueue = new AsyncMessageQueue(this.mqWrapper, rmqReceiveQueueName, rmqFactory);
        this.messageSubscriber = messageSubscriber;
    }

    // this only can be constructed with mocks
    // @VisibleForTesting
    EventLoop(
            Logger log,
            ConcurrentLinkedQueue<Event> events,
            DrainableHttpClient http,
            EventedHttpClient eventedHttp,
            EventedMessageQueue eventedMq,
            AsyncMessageQueue asyncMessageQueue,
            Consumer<MessageHandler.Props> messageSubscriber) {
        this.log = log;
        this.events = events;
        this.http = http;
        this.httpWrapper = eventedHttp;
        this.mqWrapper = eventedMq;
        this.asyncMessageQueue = asyncMessageQueue;
        this.messageSubscriber = messageSubscriber;
    }

    public HttpClient getHttp() {
        return httpWrapper;
    }

    public MessageQueue getMessageQueue() {
        return mqWrapper;
    }

    /**
     * Submit an event to be processed later
     */
    public synchronized void submit(Event e) {  // thread-safe
        submitCalls.incrementAndGet();
        events.offer(e);
        notifyCalls.incrementAndGet();
        notify();  // wake up event loop
    }

    /**
     * Disable event processing
     */
    public synchronized void stop() {  // thread-safe
        running = false;
        notifyCalls.incrementAndGet();
        notify();  // if the event loop is empty, we will wait and stop() never halts the loop, so force a wake up
    }

    @Override
    public void run() {
        if (!running) {
            running = true;
        }
        asyncMessageQueue.start();
        while (running) {
            // double locking with wait to avoid using up all cpu resources when the queue is empty
            if (events.isEmpty()) {
                synchronized (this) {
                    if (events.isEmpty()) {
                        try {
                            waitCalls.incrementAndGet();
                            wait();  // block to wait for producer
                        } catch (InterruptedException e) {
                            running = false;
                            log.warn("wait was interrupted, will stop event loop. %s", e);
                            break;
                        }
                    }
                }
            }
            processSingleEvent();
        }
        asyncMessageQueue.stop();
    }

    // @VisibleForTesting
    void processSingleEvent() {
        processSingleEventCalls.incrementAndGet();
        if (events.isEmpty()) {  // poll() is non-blocking
            return;
        }
        var event = events.poll();
        if (event == null) {
            throw new ConcurrentModificationException("multiple consumers detected on the event loop queue");
        } if (event.type() == null) {
            this.handleInvalidEventType(event);
        } else {
            switch (event.type()) {
                case HTTP_SEND -> this.handleHttpSend(event);
                case HTTP_RECEIVE -> this.handleHttpReceive(event);
                case MQ_SEND -> this.handleMessageQueueSend(event);
                case MQ_RECEIVE -> this.handleMessageQueueReceive(event);
                case DRAIN_HTTP -> this.handleDrainHttp(event);
            }
        }
    }

    // @VisibleForTesting
    void handleHttpSend(Event e) {
        var data = (EventedHttpClient.SendEventData) e.data();
        var next = data.next();
        http.send(data.req(), res -> {
            submit(Events.HTTP_RECEIVE.wrap(new EventedHttpClient.ReceiveEventData(res, next)));
        });
        submit(Events.DRAIN_HTTP.wrap(null));  // TODO smarter drain scheduling
    }

    // @VisibleForTesting
    void handleHttpReceive(Event event) {
        var data = (EventedHttpClient.ReceiveEventData) event.data();
        try {
            data.next().accept(data.res());
        } catch (Exception e) {
            log.error("http callback failed: %s", e);
            e.printStackTrace();
        }
    }

    // @VisibleForTesting
    void handleMessageQueueSend(Event event) {
        var data = (EventedMessageQueue.SendEventData) event.data();
        asyncMessageQueue.sendAsync(data.message());
    }

    // @VisibleForTesting
    void handleMessageQueueReceive(Event e) {
        var data = (EventedMessageQueue.ReceiveEventData) e.data();
        asyncMessageQueue.markReceived(data.messageId());
        messageSubscriber.accept(data.message());
    }

    // @VisibleForTesting
    void handleDrainHttp(Event e) {
        if (http.drain()) {
            // "recursively" enqueue another drain call, since the draining is not yet complete
            // TODO this will result in a ton of queue pollution and consequently CPU overload
            submit(Events.DRAIN_HTTP.wrap(null));
        }
    }

    // @VisibleForTesting
    void handleInvalidEventType(Event e) {
        // TODO metrics.emit("BadEvent")
        if (e == null) {
            log.warn("Discarding null event");
        } else {
            log.warn("Discarding event %d with invalid type %s", e.id(), e.type());
        }
    }
}
