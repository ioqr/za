package za.engine.mq;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import za.engine.MessageHandler;

public class MockMQClient implements RabbitMQClient {
    private final LinkedBlockingQueue<MessageWithId> messages = new LinkedBlockingQueue<>();

    private record MessageWithId(String id, MessageHandler.Props message) {}

    public void addMockReceivableMessage(String messageId, MessageHandler.Props message) {
        messages.offer(new MessageWithId(messageId, message));
    }

    @Override
    public void send(String queueName, String message) throws IOException, TimeoutException {
        // do nothing for now
    }

    @Override
    public void receiveBlocking(String queueName, Supplier<Boolean> canReceiveMore, BiConsumer<String, MessageHandler.Props> onReceive)
    throws InterruptedException, IOException, TimeoutException {
        while (true) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (messages.isEmpty() || !canReceiveMore.get()) {
                Thread.yield();
                continue;
            }
            var mwid = messages.poll();
            if (mwid == null) {
                Thread.yield();
                continue;
            }
            onReceive.accept(mwid.id(), mwid.message());
        }
    }
}
