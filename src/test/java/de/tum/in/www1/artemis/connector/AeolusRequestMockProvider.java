package de.tum.in.www1.artemis.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.hamcrest.text.MatchesPattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;

/**
 * Mocks requests to Aeolus
 */
@Component
@Profile("aeolus | localci")
public class AeolusRequestMockProvider {

    private final RestTemplate restTemplate;

    @Value("${aeolus.url}")
    private URL aeolusUrl;

    private MockRestServiceServer mockServer;

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructor for the AeolusRequestMockProvider
     *
     * @param restTemplate the rest template to use
     */
    public AeolusRequestMockProvider(@Qualifier("aeolusRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Enables mocking of requests
     */
    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    /**
     * Mocks a successful generate build plan request
     *
     * @param target      the target to publish to
     * @param expectedKey the expected key
     */
    public void mockSuccessfulPublishBuildPlan(AeolusTarget target, String expectedKey) throws JsonProcessingException {
        final var uriPattern = Pattern.compile(aeolusUrl + "/publish/" + target.getName());

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("key", expectedKey);
        responseBody.put("result", "imagine a result here");
        String json = objectMapper.writeValueAsString(responseBody);

        mockServer.expect(requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).body(json).contentType(org.springframework.http.MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks a failed publish build plan request
     *
     * @param target the target to publish to
     */
    public void mockFailedPublishBuildPlan(AeolusTarget target) {
        final var uriPattern = Pattern.compile(aeolusUrl + "/publish/" + target.getName());

        mockServer.expect(requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * Mocks a failed generate build plan request
     *
     * @param target the target to generate for
     */
    public void mockFailedGenerateBuildPlan(AeolusTarget target) {
        final var uriPattern = Pattern.compile(aeolusUrl + "/generate/" + target.getName());

        mockServer.expect(requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * Mocks a successful generate preview request
     *
     * @param target the target to generate for
     */
    public void mockGeneratePreview(AeolusTarget target) throws JsonProcessingException {
        final var uriPattern = Pattern.compile(aeolusUrl + "/generate/" + target.getName());

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("result", "imagine a result here");
        String json = objectMapper.writeValueAsString(responseBody);

        mockServer.expect(requestTo(MatchesPattern.matchesPattern(uriPattern))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).body(json).contentType(org.springframework.http.MediaType.APPLICATION_JSON));
    }

    public void mockAuthenticatedRequest(String uri, String token) {
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer " + token)).andRespond(withStatus(HttpStatus.OK));
    }

    /**
     * Resets the mock servers
     */
    public void reset() throws Exception {
        if (mockServer != null) {
            mockServer.reset();
        }
    }
}
