package de.tum.cit.aet.artemis.core.connector;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Mock provider for Hyperion REST API requests during testing.
 * Provides sophisticated mocking capabilities for HTTP interactions with Hyperion service.
 * Follows the same pattern as AthenaRequestMockProvider but modernized for RestClient.
 */
@Component
@Profile(PROFILE_HYPERION)
@Lazy
public class HyperionRequestMockProvider {

    private final RestTemplate hyperionRestTemplate;

    private final RestTemplate shortTimeoutHyperionRestTemplate;

    private MockRestServiceServer mockServer;

    private MockRestServiceServer mockServerShortTimeout;

    @Value("${artemis.hyperion.url}")
    private String hyperionUrl;

    @Autowired
    private ObjectMapper mapper;

    private AutoCloseable closeable;

    // Test constants for consistency checking
    public static final String MOCK_INCONSISTENCY_RESULT = "Found 2 inconsistencies: Missing test case for edge condition, Unclear problem statement in section 3";

    public static final String MOCK_REWRITTEN_STATEMENT = "Enhanced problem statement: Create a robust sorting algorithm that handles edge cases and provides optimal performance for various input sizes. Implement comprehensive error handling and include unit tests.";

    public HyperionRequestMockProvider(@Qualifier("hyperionRestTemplate") RestTemplate hyperionRestTemplate,
            @Qualifier("shortTimeoutHyperionRestTemplate") RestTemplate shortTimeoutHyperionRestTemplate) {
        this.hyperionRestTemplate = hyperionRestTemplate;
        this.shortTimeoutHyperionRestTemplate = shortTimeoutHyperionRestTemplate;
    }

    /**
     * Enable mocking of HTTP requests to Hyperion service.
     * Must be called before each test that uses mocked requests.
     */
    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(hyperionRestTemplate);
        mockServerShortTimeout = MockRestServiceServer.createServer(shortTimeoutHyperionRestTemplate);
        closeable = MockitoAnnotations.openMocks(this);
    }

    /**
     * Reset all mock servers and close resources.
     * Should be called after each test.
     */
    public void reset() throws Exception {
        if (mockServer != null) {
            mockServer.reset();
        }
        if (mockServerShortTimeout != null) {
            mockServerShortTimeout.reset();
        }
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Verify that all expected requests were made.
     */
    public void verify() {
        if (mockServer != null) {
            mockServer.verify();
        }
        if (mockServerShortTimeout != null) {
            mockServerShortTimeout.verify();
        }
    }

    // ===== HEALTH CHECK MOCKING =====

    /**
     * Mock successful health check response.
     */
    public void mockHealthCheckSuccess() {
        ObjectNode healthResponse = mapper.createObjectNode();
        healthResponse.put("status", "UP");
        healthResponse.put("version", "1.0.0");
        healthResponse.put("timestamp", System.currentTimeMillis());

        mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/health")).andExpect(method(HttpMethod.GET)).andExpect(header("X-API-Key", "test-api-key"))
                .andRespond(withSuccess(healthResponse.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mock health check failure scenarios.
     */
    public void mockHealthCheckFailure() {
        mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/health")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    }

    /**
     * Mock health check timeout.
     */
    public void mockHealthCheckTimeout() {
        mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/health")).andExpect(method(HttpMethod.GET))
                .andRespond(withException(new SocketTimeoutException("Connection timeout")));
    }

    // ===== CONSISTENCY CHECK MOCKING =====

    /**
     * Mock successful consistency check with provided result.
     *
     * @param exerciseId         The expected exercise ID in the request
     * @param result             The consistency check result to return
     * @param additionalMatchers Additional request matchers for validation
     */
    public void mockConsistencyCheckSuccess(Long exerciseId, String result, RequestMatcher... additionalMatchers) {
        ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/api/v1/exercises/" + exerciseId + "/check-consistency"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-API-Key", "test-api-key")).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        // Apply additional matchers for request validation
        for (RequestMatcher matcher : additionalMatchers) {
            responseActions.andExpect(matcher);
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("inconsistencies", result);
        response.put("analysis_duration_ms", 1500);
        response.put("total_checks_performed", 15);

        responseActions.andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mock consistency check with default success response.
     */
    public void mockConsistencyCheckSuccess(Long exerciseId) {
        mockConsistencyCheckSuccess(exerciseId, MOCK_INCONSISTENCY_RESULT);
    }

    /**
     * Mock consistency check failure scenarios.
     */
    public void mockConsistencyCheckFailure(Long exerciseId, HttpStatus status) {
        ObjectNode errorResponse = mapper.createObjectNode();
        errorResponse.put("error", "Consistency check failed");
        errorResponse.put("details", "Service temporarily unavailable");

        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/api/v1/exercises/" + exerciseId + "/check-consistency")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).body(errorResponse.toString()).contentType(MediaType.APPLICATION_JSON));
    }

    /**
     * Mock consistency check network error.
     */
    public void mockConsistencyCheckNetworkError(Long exerciseId) {
        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/api/v1/exercises/" + exerciseId + "/check-consistency")).andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Network error")));
    }

    // ===== PROBLEM STATEMENT REWRITING MOCKING =====

    /**
     * Mock successful problem statement rewriting.
     *
     * @param originalText       The expected original text in the request
     * @param rewrittenText      The rewritten text to return
     * @param additionalMatchers Additional request matchers
     */
    public void mockRewriteProblemStatementSuccess(String originalText, String rewrittenText, RequestMatcher... additionalMatchers) {
        ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/api/v1/rewrite-problem-statement")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-api-key")).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        // Apply additional matchers
        for (RequestMatcher matcher : additionalMatchers) {
            responseActions.andExpect(matcher);
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("rewritten_text", rewrittenText);
        response.put("improvements_made", "Enhanced clarity, added technical details, improved structure");
        response.put("processing_time_ms", 800);

        responseActions.andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mock problem statement rewriting with default response.
     */
    public void mockRewriteProblemStatementSuccess(String originalText) {
        mockRewriteProblemStatementSuccess(originalText, MOCK_REWRITTEN_STATEMENT);
    }

    /**
     * Mock problem statement rewriting failure.
     */
    public void mockRewriteProblemStatementFailure(HttpStatus status) {
        ObjectNode errorResponse = mapper.createObjectNode();
        errorResponse.put("error", "Rewriting failed");
        errorResponse.put("details", "Unable to process the provided text");

        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/api/v1/rewrite-problem-statement")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).body(errorResponse.toString()).contentType(MediaType.APPLICATION_JSON));
    }

    // ===== UTILITY METHODS =====

    /**
     * Create a request matcher that validates JSON path content.
     */
    public static RequestMatcher jsonPath(String expression, Object expectedValue) {
        return org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath(expression).value(expectedValue);
    }

    /**
     * Create a request matcher that validates the presence of a JSON field.
     */
    public static RequestMatcher hasJsonPath(String expression) {
        return org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath(expression).exists();
    }

    /**
     * Mock any request to fail with a specific HTTP status.
     */
    public void mockAnyRequestFailure(HttpStatus status) {
        mockServer.expect(ExpectedCount.manyTimes(), requestTo(hyperionUrl + "/**")).andRespond(withStatus(status));

        mockServerShortTimeout.expect(ExpectedCount.manyTimes(), requestTo(hyperionUrl + "/**")).andRespond(withStatus(status));
    }
}
