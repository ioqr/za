package za.engine;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.rabbitmq.client.*;

import za.lib.Logger;

public class RabbitMQClient {
    public static final String INPUT_QUEUE_NAME  = "za.i";
    public static final String OUTPUT_QUEUE_NAME = "za.o";
    
    private final ConnectionFactory connectionFactory;
    
    private RabbitMQClient(String username, String password, String virtualHost, String host, int port) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        factory.setHost(host);
        factory.setPort(port);
        connectionFactory = factory;
    }

    public interface Factory extends Supplier<RabbitMQClient> {}

    public static RabbitMQClient.Factory factory(String username, String password, String virtualHost, String host, int port) {
        return () -> new RabbitMQClient(username, password, virtualHost, host, port);
    }
    
    public void send(String queueName, String message) throws IOException, TimeoutException {
        // TODO reusable connections
        try (var connection = connectionFactory.newConnection();
             var channel = connection.createChannel();) {
            channel.queueDeclare(queueName, false, false, false, null);
            channel.basicPublish("", queueName, null, message.getBytes());
        }
    }

    public void receiveBlocking(String queueName, Consumer<String> onMessage) throws IOException, TimeoutException {
        var connection = connectionFactory.newConnection();
        var channel = connection.createChannel();
        var deliveryCallback = new DeliveryCallbackImpl(onMessage);
        var cancelCallback = new CancelCallbackImpl();
        channel.queueDeclare(queueName, false, false, false, null);
        channel.basicConsume(queueName, true, deliveryCallback, cancelCallback);
    }
    
    class DeliveryCallbackImpl implements DeliverCallback {
        private Logger log = Logger.verbose(DeliveryCallbackImpl.class);
        private final Consumer<String> onMessage;

        DeliveryCallbackImpl(Consumer<String> onMessage) {
            this.onMessage = onMessage;
        }
        
        @Override
        public void handle(String consumerTag, Delivery delivery) throws IOException {
            var message = new String(delivery.getBody(), "UTF-8");
            log.info("Received '%s' (tag='%s')", message, consumerTag);
            onMessage.accept(message);
        }
    }
    
    class CancelCallbackImpl implements CancelCallback {
        private Logger log = Logger.verbose(CancelCallbackImpl.class);
        
        @Override
        public void handle(String consumerTag) throws IOException {
            log.warn("Cancel for consumerTag='%s'", consumerTag);
            // TODO: notify consumer of the cancellation
        }
    }
}
