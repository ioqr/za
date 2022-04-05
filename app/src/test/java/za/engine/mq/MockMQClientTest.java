package za.engine.mq;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import za.engine.InternalMessage;
import za.engine.MessageListener;

public class MockMQClientTest {
    public static final int WAIT_TIMEOUT_MS = 10000;  // should be long enough to be annoying

    @Test
    public void testMockMQClient() throws InterruptedException {
        MockMessageClient client = new MockMessageClient();
        // check that mq.send() works out of the box
        try {
            client.send("queueName", "message");
        } catch (Exception e) {
            fail(e);
        }
        // enqueue some tester messages
        Map<String, InternalMessage> sharedMap = new ConcurrentHashMap<>();
        final int messages = 100;
        for (int i = 0; i < messages; i++) {
            var messageId = "mock-" + UUID.randomUUID();
            var message = new InternalMessage(
                UUID.randomUUID(),
                "context-" + messageId,
                "channel-" + messageId,
                "pluginId-" + messageId,
                Optional.of(messageId),
                Map.of("mock", true, "id", messageId));
            client.addMockReceivableMessage(message);
            sharedMap.put(messageId, message);
        }
        var lock = new Object();
        var mq = new MessageQueueImpl(sharedMap, lock);
        // test that receiveBlocking will process all enqueued messages
        var thd = new Thread(() -> {
            try {
                client.receiveBlocking("ignored-queue-name", () -> true, mq::onReceive);
            } catch (IOException | TimeoutException e) {
                // these exceptions do not occur in the mock implementation
                fail(e);  // TODO is fail() thread-safe?
            } catch (InterruptedException _ignored) {
                System.out.println("MockMQClientTest: recv interrupt has ended receiving");
            }
        }, "testMockMQClient_Receiver_" + UUID.randomUUID());
        thd.start();
        synchronized (lock) {
            lock.wait(WAIT_TIMEOUT_MS);
        }
        thd.interrupt();
        thd.join();
        assertTrue(sharedMap.isEmpty(), "the mq did not receive all mock messages");
    }

    private record MessageQueueImpl(Map<String, InternalMessage> messageMap, Object lock) implements MessageListener {
        @Override
        public void onSend(InternalMessage message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onReceive(InternalMessage receivedMessage) {  // thread-safe
            if (receivedMessage.messageId().isEmpty()) {
                throw new IllegalStateException("Message id must not be empty key = " + receivedMessage.key());
            }
            String messageId = receivedMessage.messageId().get();
            InternalMessage message = messageMap.get(messageId);
            if (message == null) {
                fail("Message with id=" + messageId + " not found in messageMap");
            } else if (!message.equals(receivedMessage)) {
                fail("Message with id=" + messageId + " did not match the known value in messageMap");
            } else {
                // if (Math.random() < 0.99)  // uncomment to trigger flakey behavior
                messageMap.remove(messageId);
                if (messageMap.isEmpty()) {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            }
        }
    }
}
