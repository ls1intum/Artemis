package de.tum.in.www1.artemis.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.SocketTimeoutException;

import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
@Profile("athena")
public class AthenaRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer mockServerShortTimeout;

    private final RestTemplate veryShortTimeoutRestTemplate;

    private MockRestServiceServer mockServerVeryShortTimeout;

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    @Autowired
    private ObjectMapper mapper;

    private AutoCloseable closeable;

    public static final String ATHENA_MODULE_TEXT_TEST = "module_text_test";

    public static final String ATHENA_RESTRICTED_MODULE_TEXT_TEST = "module_text_test_restricted";

    public static final String ATHENA_MODULE_PROGRAMMING_TEST = "module_programming_test";

    public static final String ATHENA_RESTRICTED_MODULE_PROGRAMMING_TEST = "module_programming_test_restricted";

    public AthenaRequestMockProvider(@Qualifier("athenaRestTemplate") RestTemplate restTemplate, @Qualifier("shortTimeoutAthenaRestTemplate") RestTemplate shortTimeoutRestTemplate,
            @Qualifier("veryShortTimeoutAthenaRestTemplate") RestTemplate veryShortTimeoutRestTemplate) {
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.veryShortTimeoutRestTemplate = veryShortTimeoutRestTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServerShortTimeout = MockRestServiceServer.createServer(shortTimeoutRestTemplate);
        mockServerVeryShortTimeout = MockRestServiceServer.createServer(veryShortTimeoutRestTemplate);
        closeable = MockitoAnnotations.openMocks(this);
    }

    public void reset() throws Exception {
        if (mockServer != null) {
            mockServer.reset();
        }

        if (mockServerShortTimeout != null) {
            mockServerShortTimeout.reset();
        }

        if (mockServerVeryShortTimeout != null) {
            mockServerVeryShortTimeout.reset();
        }

        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Returns the name of the test module for the given module type
     *
     * @param moduleType The type of the module: "text" or "programming"
     */
    private static String getTestModuleName(String moduleType) {
        return "module_" + moduleType + "_test";
    }

    /**
     * Mocks the /submissions API from Athena used to submit all submissions of an exercise.
     *
     * @param moduleType       The type of the module: "text" or "programming"
     * @param expectedContents The expected contents of the request
     */
    public void mockSendSubmissionsAndExpect(String moduleType, RequestMatcher... expectedContents) {
        ResponseActions responseActions = mockServer
                .expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules/" + moduleType + "/" + getTestModuleName(moduleType) + "/submissions"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        for (RequestMatcher matcher : expectedContents) {
            responseActions = responseActions.andExpect(matcher);
        }

        // Response: {"status":200,"data":null,"module_name":"module_text_test"}
        final ObjectNode node = mapper.createObjectNode().put("status", 200).put("module_name", getTestModuleName(moduleType)).putNull("data");
        responseActions.andRespond(withSuccess(node.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks the /select_submission API from Athena used to select a submission for manual assessment
     *
     * @param moduleType           The type of the module: "text" or "programming"
     * @param submissionIdResponse The submission id to return from the endpoint. An ID of -1 means "no selection".
     * @param expectedContents     The expected contents of the request
     */
    public void mockSelectSubmissionsAndExpect(String moduleType, long submissionIdResponse, RequestMatcher... expectedContents) {
        ResponseActions responseActions = mockServerVeryShortTimeout
                .expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules/" + moduleType + "/" + getTestModuleName(moduleType) + "/select_submission"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        for (RequestMatcher matcher : expectedContents) {
            responseActions.andExpect(matcher);
        }

        // Response: e.g. {"status":200,"data":<submissionIdResponse>,"module_name":"module_example"}
        final ObjectNode node = mapper.createObjectNode().put("status", 200).put("module_name", getTestModuleName(moduleType)).put("data", submissionIdResponse);

        responseActions.andRespond(withSuccess(node.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks the /select_submission API from Athena used to retrieve the selected submission for manual assessment
     * with a server error.
     *
     * @param moduleType The type of the module: "text" or "programming"
     */
    public void mockGetSelectedSubmissionAndExpectNetworkingException(String moduleType) {
        mockServerVeryShortTimeout.expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules/" + moduleType + "/" + getTestModuleName(moduleType) + "/select_submission"))
                .andExpect(method(HttpMethod.POST)).andRespond(withException(new SocketTimeoutException("Mocked SocketTimeoutException")));
    }

    /**
     * Mocks the /feedbacks API from Athena used to submit feedbacks for a submission
     *
     * @param moduleType       The type of the module: "text" or "programming"
     * @param expectedContents The expected contents of the request
     */
    public void mockSendFeedbackAndExpect(String moduleType, RequestMatcher... expectedContents) {
        ResponseActions responseActions = mockServer
                .expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules/" + moduleType + "/" + getTestModuleName(moduleType) + "/feedbacks"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        for (RequestMatcher matcher : expectedContents) {
            responseActions.andExpect(matcher);
        }

        // Response: e.g. {"status":200,"data":null,"module_name":"module_text_test"}
        final ObjectNode node = mapper.createObjectNode().put("status", 200).put("module_name", getTestModuleName(moduleType)).putNull("data");

        responseActions.andRespond(withSuccess(node.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks the /feedback_suggestions API from Athena used to retrieve feedback suggestions for a submission
     * Makes the endpoint return one example feedback suggestion.
     *
     * @param moduleType       The type of the module: "text" or "programming"
     * @param expectedContents The expected contents of the request
     */
    public void mockGetFeedbackSuggestionsAndExpect(String moduleType, RequestMatcher... expectedContents) {
        ResponseActions responseActions = mockServer
                .expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules/" + moduleType + "/" + getTestModuleName(moduleType) + "/feedback_suggestions"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        for (RequestMatcher matcher : expectedContents) {
            responseActions.andExpect(matcher);
        }

        ObjectNode suggestion = mapper.createObjectNode().put("id", 1L).put("exerciseId", 1L).put("submissionId", 1L).put("title", "Not so good")
                .put("description", "This needs to be improved").put("credits", -1.0);
        if (moduleType.equals("text")) {
            suggestion = suggestion.put("indexStart", 3).put("indexEnd", 9);
        }
        else if (moduleType.equals("programming")) {
            suggestion = suggestion.put("lineStart", 3).put("lineEnd", 4);
        }
        else {
            throw new IllegalArgumentException("Unknown module type: " + moduleType);
        }

        final ObjectNode node = mapper.createObjectNode().put("module_name", getTestModuleName(moduleType)).put("status", 200).set("data",
                mapper.createArrayNode().add(suggestion));

        responseActions.andRespond(withSuccess(node.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks the /modules API from Athena used to retrieve all available feedback suggestion modules
     * Makes the endpoint return an empty module list.
     *
     */
    public void mockGetAvailableModulesSuccessEmptyModulesList() {
        // Response: []
        final ArrayNode array = mapper.createArrayNode();

        final ResponseActions responseActions = mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules")).andExpect(method(HttpMethod.GET));
        responseActions.andRespond(withSuccess().body(array.toString()).contentType(MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks the /modules API from Athena used to retrieve all available feedback suggestion modules
     * Makes the endpoint return four modules (2x text and 2x programming).
     *
     */
    public void mockGetAvailableModulesSuccess() {
        // Response:
        // [{"name":"module_example","url":"http://module-example-service:5001","type":"programming","supports_evaluation":true},{"name":"module_programming_llm","url":"http://module-programming-llm-service:5002","type":"programming","supports_evaluation":false},{"name":"module_text_llm","url":"http://module-text-llm-service:5003","type":"text","supports_evaluation":true},{"name":"module_text_cofee","url":"http://module-text-cofee-service:5004","type":"text","supports_evaluation":false},{"name":"module_programming_themisml","url":"http://module-programming-themisml-service:5005","type":"programming","supports_evaluation":false}]
        final ArrayNode array = mapper.createArrayNode();

        array.add(createModule(ATHENA_MODULE_TEXT_TEST, "http://module-text-test-service:5001", "text", true));
        array.add(createModule(ATHENA_MODULE_PROGRAMMING_TEST, "http://module-programming-test-service:5002", "programming", false));
        array.add(createModule(ATHENA_RESTRICTED_MODULE_TEXT_TEST, "http://module-restricted-text-service:5004", "text", false));
        array.add(createModule(ATHENA_RESTRICTED_MODULE_PROGRAMMING_TEST, "http://module-restricted-programming-test-service:5003", "programming", true));

        final ResponseActions responseActions = mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules")).andExpect(method(HttpMethod.GET));
        responseActions.andRespond(withSuccess().body(array.toString()).contentType(MediaType.APPLICATION_JSON));
    }

    /**
     * Helper method to create a JSON representation of a feedback module as sent by Athena
     *
     * @param name               The name of the module
     * @param url                The URL of the module
     * @param type               The type for which the module can generate feedback suggestions
     * @param supportsEvaluation Indicating if the module can support evaluation (not used in Artemis)
     * @return JSON representation of the feedback module
     */
    private ObjectNode createModule(String name, String url, String type, boolean supportsEvaluation) {
        // creates {"name":"module_example","url":"http://module-example-service:5001","type":"programming","supports_evaluation":true}
        ObjectNode moduleNode = mapper.createObjectNode();
        moduleNode.put("name", name);
        moduleNode.put("url", url);
        moduleNode.put("type", type);
        moduleNode.put("supports_evaluation", supportsEvaluation);
        return moduleNode;
    }

    /**
     * Mocks the /health API endpoint from Athena used to check if the service is up and running
     *
     * @param exampleModuleHealthy Example module health status (in addition to the general status)
     */
    public void mockHealthStatusSuccess(boolean exampleModuleHealthy) {
        // Response: {"status":"ok","modules":{"module_example":{"url":"http://localhost:5001","type":"programming","healthy":true}}
        final ObjectNode node = mapper.createObjectNode();
        node.set("status", mapper.valueToTree("ok"));
        final ObjectNode modules = mapper.createObjectNode();
        final ObjectNode moduleExampleNode = mapper.createObjectNode();
        moduleExampleNode.set("url", mapper.valueToTree("http://localhost:5001"));
        moduleExampleNode.set("type", mapper.valueToTree("programming"));
        moduleExampleNode.set("healthy", mapper.valueToTree(exampleModuleHealthy));
        modules.set("module_example", moduleExampleNode);
        node.set("modules", modules);

        final ResponseActions responseActions = mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(athenaUrl + "/health")).andExpect(method(HttpMethod.GET));
        responseActions.andRespond(withSuccess().body(node.toString()).contentType(MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks /health API failure from Athena used to check if the service is up and running
     */
    public void mockHealthStatusFailure() {
        final ResponseActions responseActions = mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(athenaUrl + "/health")).andExpect(method(HttpMethod.GET));
        responseActions.andRespond(withException(new SocketTimeoutException()));
    }

    /**
     * Verify the requests made to Athena
     */
    public void verify() {
        mockServer.verify();
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }
}
