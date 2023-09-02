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
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
@Profile("athena")
public class AthenaRequestMockProvider {

    private static final String MODULE_EXAMPLE = "module_example";

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
     * Mocks the /submissions API from Athena used to submit all submissions of an exercise.
     *
     * @param expectedContents The expected contents of the request
     */
    public void mockSendSubmissionsAndExpect(RequestMatcher... expectedContents) {
        ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules/text/module_text_cofee/submissions"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        for (RequestMatcher matcher : expectedContents) {
            responseActions = responseActions.andExpect(matcher);
        }

        // Response: {"status":200,"data":null,"module_name":"module_example"}
        final ObjectNode node = mapper.createObjectNode().put("status", 200).put("module_name", MODULE_EXAMPLE).putNull("data");
        responseActions.andRespond(withSuccess(node.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks the /select_submission API from Athena used to select a submission for manual assessment
     *
     * @param submissionIdResponse The submission id to return from the endpoint. An ID of -1 means "no selection".
     * @param expectedContents     The expected contents of the request
     */
    public void mockSelectSubmissionsAndExpect(long submissionIdResponse, RequestMatcher... expectedContents) {
        ResponseActions responseActions = mockServerVeryShortTimeout.expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules/text/module_text_cofee/select_submission"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        for (RequestMatcher matcher : expectedContents) {
            responseActions.andExpect(matcher);
        }

        // Response: {"status":200,"data":<submissionIdResponse>,"module_name":"module_example"}
        final ObjectNode node = mapper.createObjectNode().put("status", 200).put("module_name", MODULE_EXAMPLE).put("data", submissionIdResponse);

        responseActions.andRespond(withSuccess(node.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks the /select_submission API from Athena used to retrieve the selected submission for manual assessment
     * with a server error.
     */
    public void mockGetSelectedSubmissionAndExpectNetworkingException() {
        mockServerVeryShortTimeout.expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules/text/module_text_cofee/select_submission")).andExpect(method(HttpMethod.POST))
                .andRespond(withException(new SocketTimeoutException("Mocked SocketTimeoutException")));
    }

    /**
     * Mocks the /feedbacks API from Athena used to submit feedbacks for a submission
     *
     * @param expectedContents The expected contents of the request
     */
    public void mockSendFeedbackAndExpect(RequestMatcher... expectedContents) {
        ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules/text/module_text_cofee/feedbacks"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        for (RequestMatcher matcher : expectedContents) {
            responseActions.andExpect(matcher);
        }

        // Response: {"status":200,"data":null,"module_name":"module_text_cofee"}
        final ObjectNode node = mapper.createObjectNode().put("status", 200).put("module_name", "module_text_cofee").putNull("data");

        responseActions.andRespond(withSuccess(node.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks the /feedback_suggestions API from Athena used to retrieve feedback suggestions for a submission
     * Makes the endpoint return one example feedback suggestion.
     *
     * @param expectedContents The expected contents of the request
     */
    public void mockGetFeedbackSuggestionsAndExpect(RequestMatcher... expectedContents) {
        ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(athenaUrl + "/modules/text/module_text_cofee/feedback_suggestions"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().contentType(MediaType.APPLICATION_JSON));

        for (RequestMatcher matcher : expectedContents) {
            responseActions.andExpect(matcher);
        }

        // Response: {"status":200,"data":<suggestions>,"module_name":"module_text_cofee"}
        final ObjectNode suggestion = mapper.createObjectNode().put("id", 1L).put("exerciseId", 1L).put("submissionId", 1L).put("title", "Not so good")
                .put("description", "This needs to be improved").put("credits", -1.0).put("indexStart", 3).put("indexEnd", 9);

        final ObjectNode node = mapper.createObjectNode().put("module_name", "module_text_cofee").put("status", 200).set("data", mapper.createArrayNode().add(suggestion));

        responseActions.andRespond(withSuccess(node.toString(), MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks ths /health API endpoint from Athena used to check if the service is up and running
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
        modules.set(MODULE_EXAMPLE, moduleExampleNode);
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
     * Ensures that there is no request to Athena
     */
    public void ensureNoRequest() {
        mockServer.verify();
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }
}
