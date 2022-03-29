package za.engine.http;

import za.lib.HttpClient;

/**
 * An engine-internal http client that supports single-threaded async execution of response callbacks
 */
public sealed interface DrainableHttpClient extends HttpClient permits AsyncDrainableHttpClient, RemoteHttpClient {
    /**
     * Non-blocking queue draining
     *
     * @return true if there are more requests to drain
     */
    boolean drain();

    /**
     * Blocking queue draining
     * 
     * Drains all requests from the http client, blocking until all requests have finished
     *
     * @throws InterruptedException if the thread was interrupted, causing the blocking loop to terminate
     */
    void drainFully() throws InterruptedException;
}
