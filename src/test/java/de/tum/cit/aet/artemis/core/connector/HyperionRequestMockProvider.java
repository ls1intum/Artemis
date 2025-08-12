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

        mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/health")).andExpect(method(HttpMethod.GET))
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
        ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/consistency-check"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-API-Key", "test-api-key")).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        // Apply additional matchers for request validation
        for (RequestMatcher matcher : additionalMatchers) {
            responseActions.andExpect(matcher);
        }

        // Create response matching the generated ConsistencyCheckResponse model
        ObjectNode response = mapper.createObjectNode();

        // Create issues array based on the result parameter
        var issuesArray = mapper.createArrayNode();

        if ("Language mapping test".equals(result)) {
            // Single issue for language mapping test
            var issue = mapper.createObjectNode();
            issue.put("category", "language-mapping");
            issue.put("description", "Language mapping test");
            issue.put("severity", "LOW");
            issue.put("suggested_fix", "Language mapping verified");
            issue.set("related_locations", mapper.createArrayNode());
            issuesArray.add(issue);
        }
        else if (result.contains("Found") && result.contains("inconsistencies")) {
            // Legacy format - create issues based on the descriptions in the result
            String[] parts = result.split(": ");
            if (parts.length > 1) {
                String descriptions = parts[1];
                String[] issueDescriptions = descriptions.split(", ");

                for (int i = 0; i < issueDescriptions.length; i++) {
                    var issue = mapper.createObjectNode();
                    issue.put("category", i == 0 ? "test-coverage" : "problem-statement");
                    issue.put("description", issueDescriptions[i]);
                    issue.put("severity", i == 0 ? "MEDIUM" : "LOW");
                    issue.put("suggested_fix", "Fix this issue");
                    issue.set("related_locations", mapper.createArrayNode());
                    issuesArray.add(issue);
                }
            }
        }
        else if (!"No issues found".equals(result)) {
            // Default: create issues for the standard mock response
            // Issue 1
            var issue1 = mapper.createObjectNode();
            issue1.put("category", "test-coverage");
            issue1.put("description", "Missing test case for edge condition");
            issue1.put("severity", "MEDIUM");
            issue1.put("suggested_fix", "Add test for null input handling");
            issue1.set("related_locations", mapper.createArrayNode());
            issuesArray.add(issue1);

            // Issue 2
            var issue2 = mapper.createObjectNode();
            issue2.put("category", "problem-statement");
            issue2.put("description", "Unclear problem statement in section 3");
            issue2.put("severity", "LOW");
            issue2.put("suggested_fix", "Clarify the requirements");
            issue2.set("related_locations", mapper.createArrayNode());
            issuesArray.add(issue2);
        }

        response.set("issues", issuesArray);

        // Add metadata
        var metadata = mapper.createObjectNode();
        metadata.put("analysis_duration_ms", 1500);
        metadata.put("total_checks_performed", 15);
        response.set("metadata", metadata);

        responseActions.andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
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
        ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/problem-statement-rewrite"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-API-Key", "test-api-key")).andExpect(content().contentType(MediaType.APPLICATION_JSON));

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
     * Mock problem statement rewriting success for specific exercise with DTO response.
     */
    public void mockProblemStatementRewriteSuccess(Long exerciseId, String rewrittenText, RequestMatcher... additionalMatchers) {
        // Delegate to the main method - exerciseId is used for test organization but not in actual API call
        mockRewriteProblemStatementSuccess("", rewrittenText, additionalMatchers);
    }

    /**
     * Mock problem statement rewriting failure.
     */
    public void mockRewriteProblemStatementFailure(HttpStatus status) {
        ObjectNode errorResponse = mapper.createObjectNode();
        errorResponse.put("error", "Rewriting failed");
        errorResponse.put("details", "Unable to process the provided text");

        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/problem-statement-rewrite")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).body(errorResponse.toString()).contentType(MediaType.APPLICATION_JSON));
    }

    // ===== UTILITY METHODS =====

    /**
     * Create a request matcher that validates the presence of a JSON field.
     */
    public static RequestMatcher hasJsonPath(String expression) {
        return org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath(expression).exists();
    }

    /**
     * Mock consistency check failure with a specific HTTP status.
     */
    public void mockConsistencyCheckFailure(HttpStatus status) {
        ObjectNode errorResponse = mapper.createObjectNode();
        errorResponse.put("error", "Consistency check failed");
        errorResponse.put("details", "Service temporarily unavailable");

        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/consistency-check")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).body(errorResponse.toString()).contentType(MediaType.APPLICATION_JSON));
    }

    /**
     * Mock consistency check network error.
     */
    public void mockConsistencyCheckNetworkError() {
        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/consistency-check")).andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Network error")));
    }

    /**
     * Mock consistency check with issues found.
     */
    public void mockConsistencyCheckWithIssues(Long exerciseId) {
        ObjectNode response = mapper.createObjectNode();

        // Create issues array with sample issues
        var issuesArray = mapper.createArrayNode();

        // Issue 1
        var issue1 = mapper.createObjectNode();
        issue1.put("category", "test-coverage");
        issue1.put("description", "Missing test case for edge condition");
        issue1.put("severity", "HIGH");
        issue1.put("suggested_fix", "Add test for null input handling");
        issue1.set("related_locations", mapper.createArrayNode());
        issuesArray.add(issue1);

        // Issue 2
        var issue2 = mapper.createObjectNode();
        issue2.put("category", "problem-statement");
        issue2.put("description", "Unclear problem statement section");
        issue2.put("severity", "MEDIUM");
        issue2.put("suggested_fix", "Clarify requirements in section 2");
        issue2.set("related_locations", mapper.createArrayNode());
        issuesArray.add(issue2);

        response.set("issues", issuesArray);
        response.put("has_issues", true);
        response.put("summary", "Found 2 consistency issues");

        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/consistency-check")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-api-key")).andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mock consistency check timeout.
     */
    public void mockConsistencyCheckTimeout(Long exerciseId) {
        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/consistency-check")).andExpect(method(HttpMethod.POST))
                .andRespond(withException(new java.net.SocketTimeoutException("Connection timeout")));
    }

    /**
     * Mock problem statement rewrite with no improvement.
     */
    public void mockProblemStatementRewriteNoImprovement(Long exerciseId, String originalText) {
        ObjectNode response = mapper.createObjectNode();
        response.put("rewritten_text", originalText);
        response.put("improved", false);

        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/problem-statement-rewrite")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-api-key")).andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mock problem statement rewrite timeout.
     */
    public void mockRewriteProblemStatementTimeout(Long exerciseId) {
        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/problem-statement-rewrite")).andExpect(method(HttpMethod.POST))
                .andRespond(withException(new java.net.SocketTimeoutException("Connection timeout")));
    }

    // ===== Additional Mock Methods for Service Tests =====

    /**
     * Mock health status network error.
     */
    public void mockHealthStatusNetworkError() {
        mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/health")).andExpect(method(HttpMethod.GET))
                .andRespond(withException(new IOException("Network error")));
    }

    /**
     * Mock problem statement rewrite network error.
     */
    public void mockRewriteProblemStatementNetworkError() {
        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/problem-statement-rewrite")).andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Network error")));
    }

    /**
     * Mock problem statement rewrite with success and custom response.
     */
    public void mockProblemStatementRewriteSuccess(Long exerciseId, String originalText, String improvedText) {
        ObjectNode response = mapper.createObjectNode();
        response.put("rewritten_text", improvedText);
        response.put("improved", true);

        mockServer.expect(ExpectedCount.once(), requestTo(hyperionUrl + "/review-and-refine/problem-statement-rewrite")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-api-key")).andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
    }
}
