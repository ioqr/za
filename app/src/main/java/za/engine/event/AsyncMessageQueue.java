package za.engine.event;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import za.engine.MessageHandler;
import za.engine.MessageQueue;
import za.engine.mq.RabbitMQClient;

public class AsyncMessageQueue {
    public static final int RECEIVE_LIMIT = 10;  // this determines the engine concurrency (TODO make configurable)
    public static final int SENDER_WAIT_TIME_MS = 1500;  // how long for poller to wait before sending an unfinished (uninterrupted) batch
    public static final int SEND_LIMIT = 1000;  // max messages allowed to be queued
    public static final int SEND_CAPACITY = SEND_LIMIT * 4;  // to account for output traffic spikes

    private final MessageQueue receiver;
    private final String receiverQueueName;
    private final Supplier<RabbitMQClient> rabbitMQClientFactory;
    private final String uuid = UUID.randomUUID().toString();
    private final ArrayBlockingQueue<BatchData> batch = new ArrayBlockingQueue<>(SEND_CAPACITY);

    private volatile boolean running;
    private Thread receiverThread;
    private Thread senderThread;
    private final AtomicInteger inFlightMessages = new AtomicInteger();

    public AsyncMessageQueue(MessageQueue receiver, String receiverQueueName, Supplier<RabbitMQClient> rabbitMQClientFactory) {
        this.receiver = receiver;
        this.receiverQueueName = receiverQueueName;
        this.rabbitMQClientFactory = rabbitMQClientFactory;
    }

    private record BatchData(String mqChannel, String serializedMessage) {}

    public void start() {
        if (running) {
            throw new IllegalStateException("AsyncMessageQueue was already started");
        }
        running = true;
        receiverThread = new Thread(() -> {
            try {
                var rmq = rabbitMQClientFactory.get();
                rmq.receiveBlocking(
                    receiverQueueName,
                    () -> {
                        // do not increment count here, this function must be a view
                        return inFlightMessages.get() < RECEIVE_LIMIT;
                    },
                    (String messageId, MessageHandler.Props message) -> {
                        if (messageId == null) {  // allow messageId to be overridden by an MQClient
                            messageId = UUID.randomUUID().toString();
                        }
                        inFlightMessages.incrementAndGet();
                        receiver.receive(messageId, message);
                    });
            } catch (Exception e) {
                throw new RuntimeException(Thread.currentThread().getName() + " failed to receive a message", e);
            }
        }, "AsyncMessageQueue_Receiver_" + uuid);
        senderThread = new Thread(() -> {
            var rmq = rabbitMQClientFactory.get();
            while (running) {
                // TODO replace with wait(SENDER_WAIT_TIME_MS) and notify() instead of using interrupt handler
                try {
                    Thread.sleep(SENDER_WAIT_TIME_MS);
                } catch (InterruptedException e) {
                    System.err.println(Thread.currentThread().getName() + " was interrupted (if you see this often, increase SEND_LIMIT)");
                    e.printStackTrace();
                }
                threadSafeSendBatch(rmq);
            }
        }, "AsyncMessageQueue_Sender_" + uuid);
        receiverThread.start();
        senderThread.start();
    }

    public void stop() {
        if (!running) {
            throw new IllegalStateException("AsyncMessageQueue has not been started");
        }
        receiverThread.interrupt();
        // do not interrupt the autoSendTimer, it will end on its own
    }
    
    public void sendAsync(MessageHandler.Props message) {
        var opt = MessageHandler.encode(message);
        if (opt.isPresent()) {
            batch.add(new BatchData(lookupMQChannel(message.context()), opt.get()));
            if (batch.size() >= SEND_LIMIT) {
                senderThread.interrupt();
            }
        } else {
            // TODO This occurs if the plugin sends something that is unserializable
            //      I do not think it is good to have the event loop crash in this case,
            //      however plugins should *never* attempt to send unserializable data..!
            new Exception("Invalid message was sent").printStackTrace(); // TODO replace with log.warn()
        }
    }

    public void markReceived(String messageId/*, boolean success*/) {  // TODO need a map of messages to ack / nack
        inFlightMessages.decrementAndGet();
    }

    private synchronized void threadSafeSendBatch(RabbitMQClient rmq) {  // thread-safe
        if (this.batch.isEmpty()) {
            return;
        }
        var batch = new ArrayList<BatchData>();
        synchronized (this) {
            this.batch.drainTo(batch);
        }
        batch.forEach(props -> {
            try {
                rmq.send(props.mqChannel(), props.serializedMessage());
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
        });
        System.err.println("AsyncMessageQueue: info: message batch was sent to the mq");
    }

    private static String lookupMQChannel(String context) {  // TODO make configurable
        return switch (context) {
            case "in" -> "za.i";
            case "out" -> "za.o";
            default -> throw new RuntimeException("Invalid context " + context);
        };
    }
}
