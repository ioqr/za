package za.engine.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;

import za.lib.Logger;

/**
 * Http client implemented with a single-threaded event loop
 * Sending and draining must be called from the same thread
 */
public final class AsyncDrainableHttpClient implements DrainableHttpClient, AutoCloseable {
    /** maximum pending requests (not yet sent to client) */
    public static final int SEND_QUEUE_BACKLOG = 1 << 15;  // TODO make configurable?

    /** the client that runs our requests */
    private final HttpClientFacade client;
    /** number of requests to send to the client before blocking */
    private final int concurrency;
    /** application logger */
    private final Logger log;

    /** queue for requests that are waiting to be sent to the client */
    private final ArrayBlockingQueue<QueueData> sendQueue;  // reader: this, writer: this TODO make regular queue, not blocking
    /** queue for responses from the client */
    private final ArrayBlockingQueue<QueueData> responseQueue;  // reader: this, writer: client
    /** count of all non-finished requests (includes sendQueue.size() + all requests not in responseQueue) */
    private int requestCount;  // reader: this, writer: this

    public AsyncDrainableHttpClient(HttpClientFacade client, int concurrency, Logger log) {
        this.client = client;
        this.concurrency = concurrency;
        this.log = log;

        // initialize state
        sendQueue = new ArrayBlockingQueue<>(SEND_QUEUE_BACKLOG);
        responseQueue = new ArrayBlockingQueue<>(concurrency);
        requestCount = 0;
    }

    /**
     * Enqueues a request (non-blocking)
     * 
     * @throw RuntimeException if send queue is maxed out
     */
    @Override
    public void send(Request req, Consumer<Response> next) {
        Objects.requireNonNull(req);
        Objects.requireNonNull(next);
        // TODO use IllegalStateException thrown by AsyncBlockingQueue.add(), instead of pre-throwing a RuntimeException?
        if (sendQueue.remainingCapacity() == 0) {
            throw new RuntimeException(String.format("Send queue reached limit at %d http requests.", sendQueue.size()));
        }
        sendQueue.add(new QueueData(req, next));  // TODO this throws IllegalStateException if we hadn't already checked capacity
        requestCount++;
    }

    /**
     * Runtime for HTTP client. Follows these rules:
     *    - Must be called in same thread as send()
     *    - Blocking unless thread is interrupted
     *    - Runs all pending callbacks (maximum = concurrency) before sending new ones
     */
    @Override
    public void drainFully() throws InterruptedException {
        Consumer<QueueData> callback = qd -> {
            // called in different thread; do not reference any mutable state here!
            try {
                responseQueue.offer(qd);
            } catch (Exception e) {
                System.err.println("Internal callback failed, exiting JVM. Bug in drainFully()! Triggered by: " + qd);
                e.printStackTrace();
                System.exit(1);
            }
        };

        while (requestCount > 0) {
            // part 1: drain send queue
            {
                int batch = Math.min(sendQueue.size(), concurrency);
                batch = Math.min(responseQueue.remainingCapacity(), batch);
                if (batch > 0) {
                    List<QueueData> qds = new ArrayList<>(batch);
                    sendQueue.drainTo(qds, batch);
                    qds.forEach(qd -> {
                        try {
                            client.send(qd, callback);
                            //Thread.sleep(1000L); TODO custom timeout or slow-down logic; should be pluggable
                        } catch (Exception e) {
                            // todo put an error into the responsequeue instead
                            log.error("Dropping request due to exception sending request: %s", qd.getRequest());
                            e.printStackTrace();
                            requestCount--;
                        }
                    });
                }
            }
            // part 2: drain response queue
            {
                List<QueueData> qds = new ArrayList<>(concurrency);
                responseQueue.drainTo(qds, concurrency /* TODO use a batchSize field instead */);
                qds.forEach(qd -> {
                    try {
                        // todo so we could just have one function in AsyncRuntime...
                        qd.getResponseCallback().accept(qd.getResponse());
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error("skipping: runtime threw exception for %s: %s", qd, e);
                    }
                });
                requestCount -= qds.size();
            }
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
