package za.engine.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.engine.InternalMessage;
import za.engine.event.lib.EventedHttpClient;
import za.engine.event.lib.EventedMessageListener;
import za.engine.http.AsyncDrainableHttpClient;
import za.engine.http.DrainableHttpClient;
import za.lib.HttpClient;
import za.lib.Logger;

public class EventLoopTest {
    // event loop internals
    private Logger mockLog;
    private ConcurrentLinkedQueue<Event> events;
    private DrainableHttpClient mockDrainableHttp;
    private EventedHttpClient mockEventedHttp;
    private EventedMessageListener mockEventedMq;
    private AsyncMessageQueue mockAsyncMessageQueue;
    private Consumer<InternalMessage> onMessage;

    // the actual event loop to test
    private EventLoop eventLoop;

    @BeforeEach
    public void setup() {
        mockLog = mock(Logger.class);
        events = spy(new ConcurrentLinkedQueue<>());
        mockDrainableHttp = mock(AsyncDrainableHttpClient.class);
        mockEventedHttp = mock(EventedHttpClient.class);
        mockEventedMq = mock(EventedMessageListener.class);
        mockAsyncMessageQueue = mock(AsyncMessageQueue.class);
        onMessage = mock(Consumer.class);
        eventLoop = spy(new EventLoop(mockLog, events, mockDrainableHttp, mockEventedHttp, mockEventedMq, mockAsyncMessageQueue, onMessage));
    }

    @Test
    public void testAsyncMessageQueueWasStartedAndStopped() {  // flakey; just re-run if it fails
        verify(mockAsyncMessageQueue, never()).start();
        verify(mockAsyncMessageQueue, never()).stop();
        final int trials = 10;
        for (int i = 0; i < trials; i++) {
            var thd = new Thread(eventLoop, "testAsyncMessageQueueWasStartedAndStopped-EventLoopThread");
            thd.start();
            Thread.yield();  // flakey hack to avoid race condition on slow computers where we call loop.stop() before loop.run() gets a chance to run
            eventLoop.stop();
            thd.interrupt();
            try {
                thd.join();
            } catch (InterruptedException e) {
                fail(e);
                return;
            }
        }
        verify(mockAsyncMessageQueue, times(trials)).start();
        verify(mockAsyncMessageQueue, times(trials)).stop();
    }

    @Test
    public void testSubmitOffersAnEventToTheQueue() {
        Event event = Events.HTTP_SEND.wrap("blarg");
        verify(events, never()).offer(event);
        eventLoop.submit(event);
        verify(events, times(1)).offer(event);
    }

    @Test
    public void testGetters() {
        assertEquals(mockEventedHttp, eventLoop.getHttp());
        assertEquals(mockEventedMq, eventLoop.getMessageQueue());
    }

    @Test
    public void testSendHttpEvents() {
        var http = new EventedHttpClient(eventLoop);
        final int trials = 10;
        for (int i = 0; i < trials; i++) {
            http.send(mock(HttpClient.Request.class), mock(Consumer.class));
            assertEquals(i+1, events.size());
        }
        assertEquals(trials, events.size());
        assertEquals(Events.HTTP_SEND, events.peek().type());
        for (int i = 0; i < trials; i++) {
            eventLoop.processSingleEvent();
        }
        verify(eventLoop, times(trials)).handleHttpSend(any());
        assertEquals(trials, events.size());
        for (Event event : events.toArray(new Event[trials])) {
            assertEquals(Events.DRAIN_HTTP, event.type());
        }
    }

    @Test
    public void testReceiveHttpEvents() {
        final int trials = 10;
        for (int i = 0; i < trials; i++) {
            // TODO use a mock http client to force the receive
            EventedHttpClient.ReceiveEventData receiveEventData = mock(EventedHttpClient.ReceiveEventData.class);
            Consumer<HttpClient.Response> emptyCallback = x -> {};
            when(receiveEventData.next()).thenReturn(emptyCallback);
            eventLoop.submit(Events.HTTP_RECEIVE.wrap(receiveEventData));
            assertEquals(i+1, events.size());
        }
        assertEquals(trials, events.size());
        assertEquals(Events.HTTP_RECEIVE, events.peek().type());
        for (int i = 0; i < trials; i++) {
            eventLoop.processSingleEvent();
        }
        verify(eventLoop, times(trials)).handleHttpReceive(any());
        assertTrue(events.isEmpty());
    }

    @Test
    public void testMessageQueueSendEvents() {
        var mq = new EventedMessageListener(eventLoop);
        final int trials = 10;
        for (int i = 0; i < trials; i++) {
            mq.onSend(mock(InternalMessage.class));
            assertEquals(i+1, events.size());
        }
        assertEquals(trials, events.size());
        assertEquals(Events.MQ_SEND, events.peek().type());
        for (int i = 0; i < trials; i++) {
            eventLoop.processSingleEvent();
        }
        verify(eventLoop, times(trials)).handleMessageQueueSend(any());
        assertTrue(events.isEmpty());
    }

    @Test
    public void testMessageQueueReceiveEvents() {
        var mq = new EventedMessageListener(eventLoop);
        final int trials = 10;
        for (int i = 0; i < trials; i++) {
            mq.onReceive(mock(InternalMessage.class));
            assertEquals(i+1, events.size());
        }
        assertEquals(trials, events.size());
        assertEquals(Events.MQ_RECEIVE, events.peek().type());
        for (int i = 0; i < trials; i++) {
            eventLoop.processSingleEvent();
        }
        verify(eventLoop, times(trials)).handleMessageQueueReceive(any());
        assertTrue(events.isEmpty());
    }

    @Test
    public void testMalformedEvents() {
        final int trials = 10;
        for (int i = 0; i < trials; i++) {
            eventLoop.submit(new Event(null, new Object()));
            assertEquals(i+1, events.size());
        }
        assertEquals(trials, events.size());
        assertEquals(null, events.peek().type());
        for (int i = 0; i < trials; i++) {
            eventLoop.processSingleEvent();
        }
        verify(eventLoop, times(trials)).handleInvalidEventType(any());
        assertTrue(events.isEmpty());
    }
}
