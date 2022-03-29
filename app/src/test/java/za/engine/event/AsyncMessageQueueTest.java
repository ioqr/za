package za.engine.event;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.engine.MessageQueue;
import za.engine.event.lib.EventedMessageQueue;
import za.engine.mq.MockMQClient;
import za.engine.mq.RabbitMQClient;

public class AsyncMessageQueueTest {
    private String receiverQueueName;
    private MessageQueue receiver;
    private Supplier<RabbitMQClient> mqFactory;
    private AsyncMessageQueue amq;

    @BeforeEach
    public void setup() {
        receiverQueueName = "mock-input-queue-name";
        receiver = mock(EventedMessageQueue.class /* need some impl (doesnt matter what) to satisfy mockito */);
        mqFactory = () -> new MockMQClient();
        amq = new AsyncMessageQueue(receiver, receiverQueueName, mqFactory);
    }

    @Test
    public void testSendingMessages() {
        fail("unimplemented testSendingMessages for amq");
    }
}
