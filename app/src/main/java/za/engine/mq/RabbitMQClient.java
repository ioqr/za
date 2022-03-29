package za.engine.mq;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import za.engine.MessageHandler;

public interface RabbitMQClient {  // TODO rename to something more generic, like "MessageQueueClient"
    void send(String queueName, String message) throws IOException, TimeoutException;
    void receiveBlocking(String queueName, Supplier<Boolean> canReceiveMore, BiConsumer<String, MessageHandler.Props> onReceive)
        throws InterruptedException, IOException, TimeoutException;

    @Deprecated  // to be removed in 0.3
    static Supplier<RabbitMQClient> factory(String username, String password, String virtualHost, String host, int port) {
        return () -> new RabbitMQClientImpl(username, password, virtualHost, host, port);
    }
}
