package za.engine.http;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import za.lib.HttpClient.Request;
import za.lib.HttpClient.Response;

/**
 * An object stored in AsyncDrainableHttpClient's send queue
 * 
 * @see AsyncDrainableHttpClient
 */
// todo rename to HttpQueueData or something better
// this class is used in a producer-consumer queue, but only 1 thread writes to it at once
public final class QueueData {
    private static AtomicLong nextId = new AtomicLong(0);  // TODO do we really need a global request id? 

    private final long id = nextId.getAndIncrement();
    private final Request request;
    private final Consumer<Response> onResponse;
    private final AtomicReference<Response> response = new AtomicReference<>();  // TODO atomic is probably overkill

    QueueData(Request request, Consumer<Response> onResponse) {
        this.request = request;
        this.onResponse = onResponse;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof QueueData other) {
            return other.id == this.id;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        // onResponse.toString() might seem weird, but it gives debug info, especially useful
        // if the onResponse func is abstracted into an AsyncRuntime or similar
        return String.format("QueueData{id=%d,request=%s,onResponse=%s,hasResponse=%b}",
            id, request.toString(), onResponse.toString(), response.get() != null);
    }

    public long getId() {
        return id;
    }

    public Request getRequest() {
        return request;
    }

    public Consumer<Response> getResponseCallback() {
        return onResponse;
    }

    public Response getResponse() {
        return response.get();
    }

    public void setResponse(Response response) {
        this.response.set(response);
    }
}
