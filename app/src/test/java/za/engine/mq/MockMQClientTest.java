package za.engine.mq;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import za.engine.MessageHandler;
import za.engine.MessageQueue;

public class MockMQClientTest {
    public static final int WAIT_TIMEOUT_MS = 10000;  // should be long enough to be annoying

    @Test
    public void testMockMQClient() throws InterruptedException {
        MockMQClient client = new MockMQClient();
        // check that mq.send() works out of the box
        try {
            client.send("queueName", "message");
        } catch (Exception e) {
            fail(e);
        }
        // enqueue some tester messages
        Map<String, MessageHandler.Props> sharedMap = new ConcurrentHashMap<>();
        final int messages = 100;
        for (int i = 0; i < messages; i++) {
            var messageId = "mock-" + UUID.randomUUID();
            var message = new MessageHandler.Props(
                "context-" + messageId,
                "channel-" + messageId,
                "pluginId-" + messageId,
                Map.of("mock", true, "id", messageId));
            client.addMockReceivableMessage(messageId, message);
            sharedMap.put(messageId, message);
        }
        var lock = new Object();
        var mq = new MessageQueueImpl(sharedMap, lock);
        // test that receiveBlocking will process all enqueued messages
        var thd = new Thread(() -> {
            try {
                client.receiveBlocking("ignored-queue-name", () -> true, mq::receive);
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

    private record MessageQueueImpl(Map<String, MessageHandler.Props> messageMap, Object lock) implements MessageQueue {
        @Override
        public void send(MessageHandler.Props message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void receive(String messageId, MessageHandler.Props receivedMessage) {  // thread-safe
            var message = messageMap.get(messageId);
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
