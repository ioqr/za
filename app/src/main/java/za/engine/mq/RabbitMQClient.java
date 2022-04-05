package za.engine.mq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.rabbitmq.client.*;

import za.engine.InternalMessage;
import za.engine.MessageUtils;
import za.lib.Logger;

public class RabbitMQClient implements MessageClient {
    private static final int QUEUE_EMPTY_WARNING_PERIOD_MS = 15000;
    private static final int CONNECT_RETRY_GAP_MS = 3000;
    private static final int MAX_CONNECT_RETRIES = 10;
   
    private final Logger log = Logger.verbose(getClass());
    private final ConnectionFactory connectionFactory;
    private final ThreadLocal<Connection> connectionPool = new ThreadLocal<>();

    public RabbitMQClient(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        createConnection();
    }
    
    public RabbitMQClient(String username, String password, String virtualHost, String host, int port) {
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
    public void receiveBlocking(final String queueName, Supplier<Boolean> canReceiveMore, Consumer<InternalMessage> onReceive)
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
            String messageId = getResponse.getProps().getMessageId();
            String encodedMessage = new String(getResponse.getBody(), StandardCharsets.UTF_8);
            UUID internalKey = UUID.randomUUID();
            Optional<InternalMessage> message = MessageUtils.decode(internalKey, messageId, encodedMessage);
            if (message.isPresent()) {
                onReceive.accept(message.get());
            } else {
                log.warn("Failed to decode message id=%s", messageId);
            }
        }
    }
}
