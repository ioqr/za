package za.engine.mq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.rabbitmq.client.*;

import za.engine.MessageHandler;
import za.engine.MessageQueue;
import za.lib.Logger;

public class RabbitMQClientImpl implements RabbitMQClient {
    private static final int QUEUE_EMPTY_WARNING_PERIOD_MS = 15000;
    private static final int CONNECT_RETRY_GAP_MS = 3000;
    private static final int MAX_CONNECT_RETRIES = 10;
   
    private final Logger log = Logger.verbose(getClass());
    private final ConnectionFactory connectionFactory;
    private final ThreadLocal<Connection> connectionPool = new ThreadLocal<>();

    public RabbitMQClientImpl(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        createConnection();
    }
    
    public RabbitMQClientImpl(String username, String password, String virtualHost, String host, int port) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        factory.setHost(host);
        factory.setPort(port);
        connectionFactory = factory;
        createConnection();
    }

    private void createConnection() {
        for (int i = 0; i < MAX_CONNECT_RETRIES; i++) {
            try {
                connectionPool.set(connectionFactory.newConnection());
                return;
            } catch (IOException | TimeoutException e1) {
                log.warn("Will retry rabbitmq connection in %d ms: %s", CONNECT_RETRY_GAP_MS, e1);
                try {
                    Thread.sleep(CONNECT_RETRY_GAP_MS);
                } catch (InterruptedException e2) {
                    throw new RuntimeException(e2);
                }
            }
        }
        throw new RuntimeException("Failed to connect to RabbitMQ after max retries " + MAX_CONNECT_RETRIES);
    }

    @Override
    public void send(String queueName, String message) throws IOException, TimeoutException {
        try (var channel = connectionPool.get().createChannel();) {
            channel.queueDeclare(queueName, false, false, false, null);
            channel.basicPublish("", queueName, null, message.getBytes());
        }
    }

    @Override
    public void receiveBlocking(final String queueName, Supplier<Boolean> canReceiveMore, BiConsumer<String, MessageHandler.Props> onReceive)
    throws InterruptedException, IOException, TimeoutException {
        var channel = connectionPool.get().createChannel();
        long lastNullCheckTime = System.currentTimeMillis();
        while (true) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (!canReceiveMore.get()) {
                Thread.yield();  // TODO wait()/notify() with the flow controller
                continue;
            }
            var getResponse = channel.basicGet(queueName, true);
            long now = System.currentTimeMillis();
            if (getResponse == null) {  // occurs if we start this node on an empty queue
                if (now - lastNullCheckTime >= QUEUE_EMPTY_WARNING_PERIOD_MS) {
                    log.warn("Queue has been empty for last %d ms", QUEUE_EMPTY_WARNING_PERIOD_MS);
                    lastNullCheckTime = now;
                }
                Thread.yield();
                continue;
            }
            lastNullCheckTime = now;
            var messageId = getResponse.getProps().getMessageId();
            var encodedMessage = new String(getResponse.getBody(), "UTF-8");
            var message = MessageHandler.decode(encodedMessage);
            if (message.isPresent()) {
                onReceive.accept(messageId, message.get());
            } else {
                log.warn("Failed to decode message id=%s", messageId);
            }
        }
    }
}
