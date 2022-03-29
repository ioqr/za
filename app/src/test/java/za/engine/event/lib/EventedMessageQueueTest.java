package za.engine.event.lib;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import za.engine.MessageHandler;
import za.engine.event.Event;
import za.engine.event.EventLoop;
import za.engine.event.Events;

public class EventedMessageQueueTest {
    private final EventLoop eventLoop = mock(EventLoop.class);

    @Test
    public void testItSubmitsMqEventsForSendAndReceive() {
        EventedMessageQueue mq = new EventedMessageQueue(eventLoop);

        String messageId = "some_message_id";
        MessageHandler.Props message = mock(MessageHandler.Props.class);
        Event expectedSendEvent = Events.MQ_SEND.wrap(new EventedMessageQueue.SendEventData(message));
        Event expectedReceiveEvent = Events.MQ_RECEIVE.wrap(new EventedMessageQueue.ReceiveEventData(messageId, message));

        verify(eventLoop, never()).submit(expectedSendEvent);
        verify(eventLoop, never()).submit(expectedReceiveEvent);

        mq.send(message);
        verify(eventLoop, times(1)).submit(expectedSendEvent);
        verify(eventLoop, never()).submit(expectedReceiveEvent);

        mq.receive(messageId, message);
        verify(eventLoop, times(1)).submit(expectedReceiveEvent);
        verify(eventLoop, times(1)).submit(expectedSendEvent);  // assert it only sent once
    }
}
