package co.threathub.ingestor.js.api;

import co.threathub.ingestor.js.exception.ScriptException;
import co.threathub.ingestor.util.Utils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class HttpApi {
    private final HttpClient client = HttpClient.newHttpClient();

    @HostAccess.Export
    public Value get(String url) {
        Map<String, Object> req = new HashMap<>();
        req.put("method", "GET");
        req.put("url", url);
        return execute(req);
    }

    @HostAccess.Export
    public Value post(String url, String body) {
        Map<String, Object> req = new HashMap<>();
        req.put("method", "POST");
        req.put("url", url);
        req.put("body", body);
        return execute(req);
    }

    @HostAccess.Export
    public Value request(Map<String, Object> request) {
        return execute(request);
    }

    private Value execute(Map<String, Object> req) {
        try {
            String method = (String) req.getOrDefault("method", "GET");
            String url = (String) req.get("url");
            String body = (String) req.get("body");
            Integer timeout = (Integer) req.getOrDefault("timeout", 2000);

            Map<String, String> headers = new HashMap<>();
            Object headersObj = req.get("headers");

            if (headersObj instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    headers.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout));

            switch (method.toUpperCase()) {
                case "GET" -> builder.GET();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                case "DELETE" -> builder.DELETE();
                default -> throw new ScriptException("Unsupported method: " + method);
            }

            headers.forEach(builder::header);

            HttpResponse<String> response = client.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            Map<String, Object> result = new HashMap<>();
            result.put("status", response.statusCode());
            result.put("body", response.body());
            result.put("headers", response.headers().map());

            String json = Utils.GSON.toJson(result);
            return Context.getCurrent().eval("js", "(" + json + ")");
        } catch (Exception ex) {
            throw new ScriptException("HTTP request failed: " + ex.getMessage(), ex);
        }
    }
}
