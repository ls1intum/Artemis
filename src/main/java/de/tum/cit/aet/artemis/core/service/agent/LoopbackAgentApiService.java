package de.tum.cit.aet.artemis.core.service.agent;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.config.AgentAccessEnabled;

/**
 * Calls the REST API of this server over the loopback interface, with the token of the request being served.
 * <p>
 * The call runs through the whole security chain again, so the token is checked, its scopes and the role of its owner apply, and the
 * call shows up in the audit log like any other.
 */
@Profile(PROFILE_CORE)
@Conditional(AgentAccessEnabled.class)
@Lazy
@Service
public class LoopbackAgentApiService implements AgentApi {

    /** Creating a programming exercise sets up repositories and a build plan, which takes a while. */
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;

    private final JsonMapper jsonMapper;

    public LoopbackAgentApiService(JsonMapper jsonMapper, @Value("${local.server.port:${server.port:8080}}") int port) {
        this.jsonMapper = jsonMapper;
        // The JDK client, because the one behind SimpleClientHttpRequestFactory cannot send PATCH. HTTP/1.1, because the server does not
        // speak cleartext HTTP/2 and the upgrade the JDK client attempts by default would only add a round trip.
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl("http://localhost:" + port).requestFactory(requestFactory).build();
    }

    @Override
    public AgentApiResponse send(HttpMethod method, String path, @Nullable Object body) {
        var request = restClient.method(method).uri(contextPath() + path).header(HttpHeaders.AUTHORIZATION, authorization()).accept(MediaType.APPLICATION_JSON,
                MediaType.TEXT_PLAIN);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(jsonMapper.writeValueAsString(body));
        }
        return execute(request);
    }

    @Override
    public AgentApiResponse sendText(HttpMethod method, String path, String text) {
        var request = restClient.method(method).uri(contextPath() + path).header(HttpHeaders.AUTHORIZATION, authorization())
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN).contentType(MediaType.TEXT_PLAIN).body(text);
        return execute(request);
    }

    private AgentApiResponse execute(RestClient.RequestBodySpec request) {
        return request.exchange((clientRequest, response) -> {
            String text = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
            return AgentApiResponse.of(jsonMapper, response.getStatusCode().value(), text);
        });
    }

    /** Keeps internal calls in the same servlet context as the MCP request, including deployments below a path prefix. */
    private String contextPath() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String path = attributes.getRequest().getContextPath();
            return "/".equals(path) ? "" : path;
        }
        return "";
    }
}
