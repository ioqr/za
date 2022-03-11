package za.engine.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.lib.HttpClient;
import za.lib.Logger;
import za.lib.HttpClient.Method;
import za.lib.HttpClient.Request;

public class AsyncDrainableHttpClientTest {
    private static final int CONCURRENCY = 1;
    private static final String MOCK_ID_HEADER = "mockId";

    private Set<String> requestIds;
    private AsyncDrainableHttpClient http;

    @BeforeEach
    public void setup() {
        this.requestIds = new HashSet<>();
        HttpClientFacade facade = (qd, handleResponse) -> {
            var id = qd.getRequest().headers().get(MOCK_ID_HEADER);
            var response = new HttpClient.Response.Builder()
                .statusCode(200)
                .headers(Map.of(MOCK_ID_HEADER, id))
                .build();
            qd.setResponse(response);
            handleResponse.accept(qd);
        };
        this.http = new AsyncDrainableHttpClient(facade, CONCURRENCY, Logger.silent());
    }

    @Test
    public void testDrainFully() throws InterruptedException {
        final int requestCount = AsyncDrainableHttpClient.SEND_QUEUE_BACKLOG;  // attempt at fuzzing for dropped-request bug
        for (int i = 0; i < requestCount; i++) {
            var id = String.valueOf(i);
            var method = Method.values()[i % Method.values().length];
            var request = new HttpClient.Request.Builder()
                .url("fake-url")
                .method(method)
                .headers(Map.of(MOCK_ID_HEADER, id))
                .build();
            requestIds.add(id);
            this.http.send(request, response -> {
                var recvId = response.headers().get(MOCK_ID_HEADER);
                assertTrue(requestIds.contains(recvId));
                requestIds.remove(recvId);
            });
        }
        assertEquals(requestIds.size(), requestCount);
        http.drainFully();
        assertEquals(requestIds.size(), 0);
    }

    @Test
    public void testThrowsRuntimeExceptionWhenSendQueueIsFull() {
        var request = mock(Request.class);
        for (int i = 0; i < AsyncDrainableHttpClient.SEND_QUEUE_BACKLOG; i++) {
            http.send(request, response -> {});
        }
        assertThrows(RuntimeException.class, () -> http.send(request, response -> {}));
    }
}
