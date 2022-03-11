package za.engine.http;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

import za.lib.HttpClient;
import za.lib.Logger;

public final class HttpClientFactory {
    private static final Logger LOG = Logger.verbose(HttpClientFactory.class);

    private HttpClientFactory() {}

    /**
     * A general purpose http client
     * Client implementation is subject to change across minor versions!
     */
    public static Supplier<DrainableHttpClient> recommended() {
        return recommended(null);
    }

    /**
     * A general purpose http client
     * Client implementation is subject to change across minor versions!
     */
    public static Supplier<DrainableHttpClient> recommended(Logger log) {
        return apache(4, 100, log);
    }

    /**
     * A preconfigured multi-threaded HTTP/1.1 client using Apache HttpComponents
     * 
     * @param threads number of threads for the service
     * @param concurrency number of concurrent requests in the underlying async server (unused)
     * @param log http logger
     * @return factory producing drainable Apache HttpComponents-backed drainable http clients
     */
    public static Supplier<DrainableHttpClient> apache(int threads, int concurrency, Logger log) {
        LOG.warn("apache factory: concurrency will be removed in a future version");
        checkThreadsAndConcurrency(threads, concurrency);
        if (IOReactorConfig.Builder.getDefaultMaxIOThreadCount() < threads) {
            LOG.warn("apache factory: will increase max IOReactorConfig.Builder threads to %d", threads);
            IOReactorConfig.Builder.setDefaultMaxIOThreadCount(threads);
        }
        return () -> {
            IOReactorConfig config = IOReactorConfig.custom()
                .setSoTimeout(Timeout.ofSeconds(5))
                .setIoThreadCount(threads)
                .build();
            CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .disableCookieManagement()
                .setIOReactorConfig(config)
                .build();
            client.start();
            HttpClientFacade facade = new ApacheHttpClientFacade(client);
            return new AsyncDrainableHttpClient(facade, concurrency,
                Optional.ofNullable(log).orElseGet(() -> Logger.verbose(HttpClientFactory.class)));
        };
    }

    /**
     * @see #remote(Logger, DrainableHttpClient, String, String)
     */
    public static Supplier<HttpClient> remote() {
        return remote(Logger.verbose(HttpClientFactory.class), null, null, null);
    }

    /**
     * @see #remote(Logger, DrainableHttpClient, String, String)
     */
    public static Supplier<HttpClient> remote(Logger log) {
        return remote(log, null, null, null);
    }

    /**
     * @see #remote(Logger, DrainableHttpClient, String, String)
     */
    public static Supplier<HttpClient> remote(DrainableHttpClient innerClient) {
        return remote(null, innerClient, null, null);
    }

    /**
     * @see #remote(Logger, DrainableHttpClient, String, String)
     */
    public static Supplier<HttpClient> remote(Logger log, DrainableHttpClient innerClient) {
        return remote(log, innerClient, null, null);
    }

    /**
     * A client that proxys all requests through the default forwarding server
     * 
     * @param log logger
     * @param innerClient http client used to make call to remote service
     * @param host host string of remote service (eg 'localhost:8080')
     * @param path http path of remote service (eg '/my-forwarding-server')
     * @return factory producing remotely-backed drainable http clients
     */
    public static Supplier<HttpClient> remote(Logger log, DrainableHttpClient innerClient, String host, String path) {
        return () -> new RemoteHttpClient(
            Optional.ofNullable(innerClient).orElseGet(
                () -> HttpClientFactory.recommended().get()),
            host,
            path);
    }

    private static void checkThreadsAndConcurrency(int threads, int concurrency) {
        if (threads < 1 || concurrency < 1) {
            throw new IllegalArgumentException("Must have threads >= 1 and concurrency >= 1");
        }
    }
}
