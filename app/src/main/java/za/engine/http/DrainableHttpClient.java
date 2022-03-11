package za.engine.http;

import za.lib.HttpClient;

/**
 * An engine-internal http client that supports single-threaded async execution of response callbacks
 */
public sealed interface DrainableHttpClient extends HttpClient permits AsyncDrainableHttpClient {
    // /**
    //  * Non-blocking queue draining
    //  * 
    //  * @return number of items drained, in range 0 to max concurrency (or -1 if there are 0 pending requests)
    //  */
    // default int drain() {
    //     throw new UnsupportedOperationException("non-blocking drain() is unimplemented");
    // }

    /**
     * Blocking queue draining
     * 
     * Drains all requests from the http client, blocking until all requests have finished
     */
    void drainFully() throws InterruptedException;
}
