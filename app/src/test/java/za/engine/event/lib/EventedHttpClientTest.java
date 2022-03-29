package za.engine.event.lib;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import za.engine.event.Event;
import za.engine.event.EventLoop;
import za.engine.event.Events;
import za.lib.HttpClient;

public class EventedHttpClientTest {
    private final EventLoop eventLoop = mock(EventLoop.class);

    @Test
    public void testItSubmitsAnHttpEvent() {
        EventedHttpClient http = new EventedHttpClient(eventLoop);
        HttpClient.Request req = mock(HttpClient.Request.class);
        @SuppressWarnings("unchecked")
        Consumer<HttpClient.Response> next = mock(Consumer.class);
        http.send(req, next);
        Event expected = Events.HTTP_SEND.wrap(new EventedHttpClient.SendEventData(req, next));
        verify(eventLoop, times(1)).submit(expected);
    }
}
