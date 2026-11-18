package pl.agh.po.vpp.data.weather;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** {@link HttpFetcher} na {@link HttpClient} z JDK – bez dodatkowych bibliotek. */
public class JdkHttpFetcher implements HttpFetcher {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;
    private final Duration timeout;

    public JdkHttpFetcher() {
        this(DEFAULT_TIMEOUT);
    }

    public JdkHttpFetcher(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public HttpResult get(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Przerwano pobieranie " + uri, e);
        }
    }
}
