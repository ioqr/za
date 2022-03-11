package za.engine.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URISyntaxException;
import java.util.function.Consumer;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.junit.jupiter.api.Test;

import za.lib.HttpClient.Method;
import za.lib.HttpClient.Request;

public class ApacheHttpClientFacadeTest {
    @Test
    @SuppressWarnings({"unchecked", "resource"})
    public void testExecutionOccurred() throws URISyntaxException {
        var client = mock(CloseableHttpAsyncClient.class);
        var facade = new ApacheHttpClientFacade(client);
        var queueData = mock(QueueData.class);
        var request = mock(Request.class);
        when(request.url()).thenReturn("http://localhost");
        when(request.method()).thenReturn(Method.GET);
        when(queueData.getRequest()).thenReturn(request);
        facade.send(queueData, mock(Consumer.class));
        verify(client, times(1)).execute(
            any(AsyncRequestProducer.class),
            any(AsyncResponseConsumer.class),
            any(FutureCallback.class));
    }

    @Test
    public void testInnerClientIsClosed() throws Exception {
        var client = mock(CloseableHttpAsyncClient.class);
        var facade = new ApacheHttpClientFacade(client);
        facade.close();
        verify(client, times(1)).close();
    }
}
