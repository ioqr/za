package za.engine.mq;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import za.engine.InternalMessage;

public class MockMessageClient implements MessageClient {
    private final LinkedBlockingQueue<InternalMessage> messages = new LinkedBlockingQueue<>();

    public void addMockReceivableMessage(InternalMessage message) {
        messages.offer(message);
    }

    @Override
    public void send(String queueName, String message) throws IOException, TimeoutException {
        // do nothing for now
    }

    @Override
    public void receiveBlocking(String queueName, Supplier<Boolean> canReceiveMore, Consumer<InternalMessage> onReceive)
    throws InterruptedException, IOException, TimeoutException {
        while (true) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (messages.isEmpty() || !canReceiveMore.get()) {
                Thread.yield();
                continue;
            }
            var message = messages.poll();
            if (message == null) {
                Thread.yield();
                continue;
            }
            onReceive.accept(message);
        }
    }
}
