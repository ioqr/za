package za.lib;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public interface HttpClient {
    public static enum Method {
        GET,
        HEAD,
        POST,
        PUT,
        DELETE,
        CONNECT,
        OPTIONS,
        TRACE,
        PATCH,
        ;
    }
    
    public static record Request(
            Method method,
            String url,
            Map<String, String> headers,
            Optional<ByteBuffer> body
    ) {
        @Override
        public String toString() {
            return String.format("Request{%s('%s')}", method.name(), url);
        }

        public Optional<String> bodyAsString() {
            return body.map(buf -> new String(buf.array()));
        }

        public static class Builder {
            public static final Method DEFAULT_METHOD = Method.GET;
            public static final Map<String, String> DEFAULT_HEADERS = Map.of(
                "Accept", "*/*",
                "User-Agent", "github.com/ioqr/za"
            );

            private Method method;
            private String url;
            private Map<String, String> headers;
            private ByteBuffer body;

            public Builder method(Method method) {
                this.method = method;
                return this;
            }

            public Builder method(String method) {
                this.method = Method.valueOf(method.toUpperCase());
                return this;
            }

            public Builder url(String url) {
                this.url = url;
                return this;
            }

            public Builder headers(Map<String, String> headers) {
                this.headers = headers;
                return this;
            }

            public Builder body(ByteBuffer body) {
                this.body = body;
                return this;
            }

            public Builder body(String body) {
                byte[] arr = body.getBytes();
                this.body = ByteBuffer.allocate(arr.length);
                this.body.put(arr);
                return this;
            }

            public Request build() {
                var method = Objects.requireNonNullElse(this.method, DEFAULT_METHOD);
                var url = Objects.requireNonNull(this.url);
                var headers = Objects.requireNonNullElse(this.headers, DEFAULT_HEADERS);
                var body = Optional.ofNullable(this.body);
                return new Request(method, url, headers, body);
            }
        }
    }
    
    public static record Response(
            int statusCode,
            Map<String, String> headers,
            ByteBuffer body
    ) {
        @Override
        public String toString() {
            return String.format("Response{%d}", statusCode);
        }

        public String bodyAsString() {
            return new String(body.array());
        }

        public static class Builder {
            public static final Map<String, String> DEFAULT_HEADERS = Map.of();
            public static final ByteBuffer DEFAULT_BODY = ByteBuffer.allocate(0);

            private int statusCode = -1;
            private Map<String, String> headers;
            private ByteBuffer body;

            public Builder statusCode(int statusCode) {
                this.statusCode = statusCode;
                return this;
            }

            public Builder headers(Map<String, String> headers) {
                this.headers = headers;
                return this;
            }

            public Builder body(ByteBuffer body) {
                this.body = body;
                return this;
            }

            public Builder body(String body) {
                byte[] arr = body.getBytes();
                this.body = ByteBuffer.allocate(arr.length);
                this.body.put(arr);
                return this;
            }

            public Response build() {
                var headers = Objects.requireNonNullElse(this.headers, DEFAULT_HEADERS);
                var body = Objects.requireNonNullElse(this.body, DEFAULT_BODY);
                return new Response(this.statusCode, headers, body);
            }
        }
    }
    
    /**
     * Non-blocking http request sending
     * 
     * Guaranteed to be in same thread, but order of next() is not guaranteed
     * 
     * Network requests will occur immediately, in a separate thread, which is why
     * we do not return a promise/future object and instead the next() function must
     * be provided as a parameter
     * 
     * @param req the request (not null)
     * @param next callback function (not null)
     */
    void send(Request req, Consumer<Response> next);

    // TODO request chaining
}
