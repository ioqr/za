package za.engine.event.lib;

import za.engine.InternalMessage;
import za.engine.MessageListener;
import za.engine.event.EventLoop;
import za.engine.event.Events;

public class EventedMessageListener implements MessageListener {
    private final EventLoop eventLoop;
    
    public EventedMessageListener(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @Override
    public void onSend(InternalMessage message) {  // thread-safe
        eventLoop.submit(Events.MQ_SEND.wrap(new SendEventData(message)));
    }

    // receive() is called by a mq object (e.g. RabbitMQClient)
    @Override
    public void onReceive(InternalMessage message) {  // thread-safe
        eventLoop.submit(Events.MQ_RECEIVE.wrap(new ReceiveEventData(message)));
    }

    public record SendEventData(InternalMessage message) {}
    public record ReceiveEventData(InternalMessage message) {}
}
