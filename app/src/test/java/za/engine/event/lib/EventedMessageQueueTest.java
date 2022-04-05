package za.engine.event.lib;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import za.engine.InternalMessage;
import za.engine.event.Event;
import za.engine.event.EventLoop;
import za.engine.event.Events;

public class EventedMessageQueueTest {
    private final EventLoop eventLoop = mock(EventLoop.class);

    @Test
    public void testItSubmitsMqEventsForSendAndReceive() {
        EventedMessageListener mq = new EventedMessageListener(eventLoop);

        String messageId = "some_message_id";
        InternalMessage message = mock(InternalMessage.class);
        Event expectedSendEvent = Events.MQ_SEND.wrap(new EventedMessageListener.SendEventData(message));
        Event expectedReceiveEvent = Events.MQ_RECEIVE.wrap(new EventedMessageListener.ReceiveEventData(message));

        verify(eventLoop, never()).submit(expectedSendEvent);
        verify(eventLoop, never()).submit(expectedReceiveEvent);

        mq.onSend(message);
        verify(eventLoop, times(1)).submit(expectedSendEvent);
        verify(eventLoop, never()).submit(expectedReceiveEvent);

        mq.onReceive(message);
        verify(eventLoop, times(1)).submit(expectedReceiveEvent);
        verify(eventLoop, times(1)).submit(expectedSendEvent);  // assert it only sent once
    }
}
