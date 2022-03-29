package za.engine.event;

public enum Events {
    HTTP_SEND,
    HTTP_RECEIVE,
    MQ_SEND,
    MQ_RECEIVE,
    DRAIN_HTTP,
    ;

    /**
     * Create an event like 'Events.PROMISE.wrap(data)' instead of 'new Event(Events.PROMISE, data)'
     */
    public Event wrap(Object data) {
        return new Event(this, data);
    }
}
