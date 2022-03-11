package za.engine.http;

import java.util.function.Consumer;

/**
 * A plugin for AsyncDrainableHttpClient
 * 
 * @since 0.1.0
 */
public interface HttpClientFacade extends AutoCloseable {
    // TODO add param for Consumer<QueueData> handleErrorResponse
    void send(QueueData qd, Consumer<QueueData> handleResponse) throws Exception;
    default void close() throws Exception {}
}
