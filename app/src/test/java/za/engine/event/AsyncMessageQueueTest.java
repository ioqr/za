package za.engine.event;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.engine.MessageListener;
import za.engine.event.lib.EventedMessageListener;
import za.engine.mq.MockMessageClient;
import za.engine.mq.MessageClient;

public class AsyncMessageQueueTest {
    private String receiverQueueName;
    private MessageListener receiver;
    private Supplier<MessageClient> mqFactory;
    private AsyncMessageQueue amq;

    @BeforeEach
    public void setup() {
        receiverQueueName = "mock-input-queue-name";
        receiver = mock(EventedMessageListener.class /* need some impl (doesnt matter what) to satisfy mockito */);
        mqFactory = () -> new MockMessageClient();
        amq = new AsyncMessageQueue(receiver, receiverQueueName, mqFactory);
    }

    @Test
    public void testSendingMessages() {
        fail("unimplemented testSendingMessages for amq");
    }
}
