package za.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import za.engine.http.HttpClientFactory;
import za.lib.HttpClient.Method;
import za.lib.HttpClient.Request;
import za.lib.Logger;

// TODO configure gradle for integs

public class HttpIntegrationTest {
    private static final String USER_AGENT = "HttpIntegrationTest/1.0";
    private static final Logger LOG = Logger.silent();//.verbose(EngineIntegrationTest.class);

    /**
     * This test exists as there is currently a bug in some HttpFacades where requests get dropped,
     * which causes the main thread to hang. The issue does not present itself in the Apache facade.
     */
    /*@Test
    public void testHttpClient() throws Exception {
        int threads = 4;
        int concurrency = 100;
        var client = HttpClientFactory.apache(threads, concurrency, null).get();
        int trials = 100;
        while (trials --> 0) {
            var x = new AtomicInteger();
            Request req = new Request(
                Method.GET,
                // docker run -p 80:80 kennethreitz/httpbin
                "http://localhost/user-agent",
                Map.of("User-Agent", USER_AGENT),
                Optional.empty());
            LOG.debug("Will send request");
            client.send(req, res -> {
                assertNotNull(res);
                LOG.info("body %s", res.bodyAsString());
                assertTrue(res.body().contains(USER_AGENT));
                for (int i = 0; i < 100 / 2; i++) {
                    client.send(req, res2 -> {
                        LOG.info("%d", x.incrementAndGet());
                        assertNotNull(res2);
                        assertTrue(res2.bodyAsString().contains(USER_AGENT));
                        client.send(req, res3 -> {
                            LOG.info("%d", x.incrementAndGet());
                            assertNotNull(res3);
                            assertTrue(res3.bodyAsString().contains(USER_AGENT));
                        });
                    });
                }
            });
            LOG.debug("Sent request, will block with call to drainFully()");
            client.drainFully();
        }
    }*/
}
