package de.tum.cit.aet.artemis.videosource.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@Lazy
@Conditional(GocastEnabled.class)
public class GocastConfiguration {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public GocastSettings gocastSettings(@Value("${artemis.tum-live.api-base-url}") String apiBaseUrl, @Value("${artemis.tum-live.web-base-url}") String webBaseUrl,
            @Value("${server.url}") String serverUrl) {
        URI apiBaseUri = validateBaseUrl(apiBaseUrl, "API base URL");
        URI webBaseUri = validateBaseUrl(webBaseUrl, "web base URL");
        URI callbackUri = validateBaseUrl(serverUrl, "Artemis server URL").resolve("/api/videosource/public/gocast/approval/callback");
        return new GocastSettings(apiBaseUri, webBaseUri, callbackUri);
    }

    @Bean
    public RestClient gocastIntegrationRestClient(RestClient.Builder builder, GocastSettings settings) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return builder.clone().baseUrl(settings.apiBaseUri().toString()).requestFactory(requestFactory).build();
    }

    static URI validateBaseUrl(String value, String label) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            boolean localHttp = "http".equals(scheme) && isExplicitLocalHost(host);
            if (!("https".equals(scheme) || localHttp) || host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getRawPath().contains("%")) {
                throw new IllegalArgumentException();
            }
            return uri;
        }
        catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be an absolute HTTPS URL or an explicit local HTTP URL without credentials, query, fragment, or encoded path",
                    exception);
        }
    }

    private static boolean isExplicitLocalHost(String host) {
        return host != null && ("localhost".equalsIgnoreCase(host) || host.endsWith(".localhost") || host.endsWith(".orb.local") || "127.0.0.1".equals(host) || "::1".equals(host));
    }

    public record GocastSettings(URI apiBaseUri, URI webBaseUri, URI callbackUri) {
    }
}
