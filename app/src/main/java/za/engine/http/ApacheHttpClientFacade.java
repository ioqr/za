package za.engine.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;

import za.lib.HttpClient;
import za.lib.Logger;
import za.lib.HttpClient.Request;
import za.lib.HttpClient.Response;

public final class ApacheHttpClientFacade implements HttpClientFacade {
    private final Logger log = Logger.verbose(ApacheHttpClientFacade.class);
    private final CloseableHttpAsyncClient client;

    public ApacheHttpClientFacade(CloseableHttpAsyncClient client) {
        this.client = client;
    }

    @Override
    public void send(QueueData queueData, Consumer<QueueData> callback) throws URISyntaxException {
        client.execute(
            SimpleRequestProducer.create(toApacheRequest(queueData.getRequest())),
            SimpleResponseConsumer.create(),
            new FutureCallback<SimpleHttpResponse>() {
                @Override
                public void completed(SimpleHttpResponse apacheRes) {
                    queueData.setResponse(toZaLibResponse(apacheRes));
                    callback.accept(queueData);
                }

                @Override
                public void failed(Exception e) {
                    // TODO handle request fail (and log it)
                    e.printStackTrace();
                    callback.accept(queueData);
                    //new RuntimeException("Apache request failed: " + queueData, e).printStackTrace();
                }

                @Override
                public void cancelled() {
                    // TODO handle request cancel (and log it)
                    System.err.println("Cancelled request: " + queueData);
                    callback.accept(queueData);
                    //new RuntimeException("Apache request cancelled: " + queueData).printStackTrace();
                }
            });
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    private static SimpleHttpRequest toApacheRequest(Request req) throws URISyntaxException {
        var uri = new URI(req.url());
        var rb = SimpleRequestBuilder.create(req.method().name())
            .setHttpHost(new HttpHost(uri.getHost(), uri.getPort()))
            //.setVersion(new ProtocolVersion("https", 1, 1))//TODO untested
            .setPath(uri.getPath());
        if (req.body().isPresent()) {
            rb.setBody(req.body().get().array(), ContentType.APPLICATION_OCTET_STREAM);
        }
        req.headers().forEach(rb::addHeader);
        return rb.build();
    }

    private static Response toZaLibResponse(SimpleHttpResponse response) {
        var code = response.getCode();
        var headers = new LinkedHashMap<String, String>();  // maintain insertion order
        var body = response.getBodyText();
        for (var header : response.getHeaders()) {
            headers.put(header.getName(), header.getValue());
        }
        return new HttpClient.Response.Builder()
            .statusCode(code)
            .headers(headers)
            .body(body)
            .build();
    }
}
