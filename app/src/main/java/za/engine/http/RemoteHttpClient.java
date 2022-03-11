package za.engine.http;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Consumer;

import za.lib.HttpClient;

/**
 * Contacts a proxy server to fulfill requests
 * (see /proxy)
 * 
 * @since 0.1.0
 */
public final class RemoteHttpClient implements HttpClient {
    public static final String DEFAULT_HOST = "localhost:45678";
    public static final String DEFAULT_PATH = "/za";

    private final DrainableHttpClient http;
    private final String host;
    private final String url;

    public RemoteHttpClient(DrainableHttpClient http) {
        this(http, null, null);
    }

    public RemoteHttpClient(DrainableHttpClient http, String host, String path) {
        Objects.requireNonNull(http);
        if (host == null) {
            host = DEFAULT_HOST;
        }
        if (path == null) {
            path = DEFAULT_PATH;
        }
        this.http = http;
        this.host = host;
        this.url = "http://" + host + path;
    }

    @Override
    public void send(Request req, Consumer<Response> next) {
        var headers = prepareHeaders(req);
        http.send(new Request(Method.POST, url, headers, req.body()), next);
    }

    // TODO write unit test
    // @VisibleForTesting
    LinkedHashMap<String, String> prepareHeaders(Request req) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("Host", host);
        headers.put("User-Agent", "za");
        headers.put("Accept", "*/*");
        headers.put("X-Za-Method", base64(req.method().name()));
        headers.put("X-Za-Url", base64(req.url()));
        // forward the headers and keep track of index to maintain sending order
        int headerIndex = 0;
        for (var header : req.headers().entrySet()) {
            // encode with base64 to preserve spaces and other quirks
            headers.put("X-Za-HK-" + headerIndex, base64(header.getKey()));
            headers.put("X-Za-HV-" + headerIndex, base64(header.getValue()));
            headerIndex++;
        }
        headers.put("X-Za-HeaderCount", String.valueOf(headerIndex));
        return headers;
    }

    private static String base64(String in) {
        return new String(Base64.getEncoder().encode(in.getBytes()));
    }
}
