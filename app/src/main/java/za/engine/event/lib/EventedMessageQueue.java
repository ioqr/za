package za.engine.event.lib;

import za.engine.MessageHandler;
import za.engine.MessageQueue;
import za.engine.event.EventLoop;
import za.engine.event.Events;

public class EventedMessageQueue implements MessageQueue {
    private final EventLoop eventLoop;
    
    public EventedMessageQueue(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @Override
    public void send(MessageHandler.Props message) {  // thread-safe
        eventLoop.submit(Events.MQ_SEND.wrap(new SendEventData(message)));
    }

    // receive() is called by a mq object (e.g. RabbitMQClient)
    @Override
    public void receive(String messageId, MessageHandler.Props message) {  // thread-safe
        eventLoop.submit(Events.MQ_RECEIVE.wrap(new ReceiveEventData(messageId, message)));
    }

    public static record SendEventData(MessageHandler.Props message) {}
    public static record ReceiveEventData(String messageId, MessageHandler.Props message) {}
}
