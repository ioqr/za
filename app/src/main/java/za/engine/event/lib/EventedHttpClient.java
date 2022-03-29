package za.engine.event.lib;

import java.util.function.Consumer;

import za.engine.event.EventLoop;
import za.engine.event.Events;
import za.lib.HttpClient;

/**
 * A general purpose http client that submits all requests to an existing event loop
 */
public class EventedHttpClient implements HttpClient {
    private final EventLoop eventLoop;

    public EventedHttpClient(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @Override
    public void send(Request req, Consumer<Response> next) {
        eventLoop.submit(Events.HTTP_SEND.wrap(new SendEventData(req, next)));
    }

    public record SendEventData(Request req, Consumer<Response> next) {}
    public record ReceiveEventData(Response res, Consumer<Response> next) {}
}
