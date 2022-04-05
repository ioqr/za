package za.engine.mq;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import za.engine.InternalMessage;

public interface MessageClient {
    void send(String queueName, String message) throws IOException, TimeoutException;
    void receiveBlocking(String queueName, Supplier<Boolean> canReceiveMore, Consumer<InternalMessage> onReceive)
        throws InterruptedException, IOException, TimeoutException;
}
