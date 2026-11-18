package de.tum.cit.aet.artemis.core.service.agent;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.util.RequestUtilService;

/**
 * Lets the tests call the REST API in the process, as there is no server listening on a port in a test: the production implementation
 * calls over the loopback interface.
 */
@Primary
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class MockMvcAgentApiService implements AgentApi {

    private final RequestUtilService request;

    private final JsonMapper jsonMapper;

    public MockMvcAgentApiService(RequestUtilService request, JsonMapper jsonMapper) {
        this.request = request;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public AgentApiResponse send(HttpMethod method, String path, @Nullable Object body) {
        var builder = MockMvcRequestBuilders.request(method, path).header(HttpHeaders.AUTHORIZATION, authorization()).accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        }
        return execute(builder);
    }

    @Override
    public AgentApiResponse sendText(HttpMethod method, String path, String text) {
        var builder = MockMvcRequestBuilders.request(method, path).header(HttpHeaders.AUTHORIZATION, authorization()).accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.TEXT_PLAIN).content(text);
        return execute(builder);
    }

    private AgentApiResponse execute(AbstractMockHttpServletRequestBuilder<?> builder) {
        try {
            var response = request.performMvcRequest(builder).andReturn().getResponse();
            return AgentApiResponse.of(jsonMapper, response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
