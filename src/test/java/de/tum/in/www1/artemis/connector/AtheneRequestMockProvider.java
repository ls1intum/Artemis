package de.tum.in.www1.artemis.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.SocketTimeoutException;
import java.util.List;

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
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.in.www1.artemis.service.dto.FeedbackConflictResponseDTO;

@Component
@Profile("athene")
public class AtheneRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer mockServerShortTimeout;

    @Value("${artemis.athene.url}")
    private String atheneUrl;

    @Autowired
    private ObjectMapper mapper;

    private AutoCloseable closeable;

    public AtheneRequestMockProvider(@Qualifier("atheneRestTemplate") RestTemplate restTemplate,
            @Qualifier("shortTimeoutAtheneRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServerShortTimeout = MockRestServiceServer.createServer(shortTimeoutRestTemplate);
        closeable = MockitoAnnotations.openMocks(this);
    }

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
     * This method mocks feedback consistency response coming from Athene
     *
     * @param feedbackConflictResponseDTOs List of response DTOs, ideally an array of feedback conflicts
     */
    public void mockFeedbackConsistency(List<FeedbackConflictResponseDTO> feedbackConflictResponseDTOs) {
        final ObjectNode node = mapper.createObjectNode();
        node.set("feedbackInconsistencies", mapper.valueToTree(feedbackConflictResponseDTOs));
        final String json = node.toString();

        mockServer.expect(ExpectedCount.once(), requestTo(atheneUrl + "/feedback_consistency")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess().body(json).contentType(MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks /submit api from Athene used to submit new exercises for clustering.
     */
    public void mockSubmitSubmissions() {
        final ObjectNode node = mapper.createObjectNode();
        node.set("detail", mapper.valueToTree("Submission successful"));
        final String json = node.toString();

        mockServer.expect(ExpectedCount.once(), requestTo(atheneUrl + "/submit")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks /queueStatus api from Athene used to retrieve metadata on processed jobs. Currently used as a health check endpoint.
     *
     * @param success Successful response or timeout.
     */
    public void mockQueueStatus(boolean success) {
        final ResponseActions responseActions = mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(atheneUrl + "/queueStatus")).andExpect(method(HttpMethod.GET));

        if (success) {
            final ObjectNode node = mapper.createObjectNode();
            node.set("total", mapper.valueToTree(42));
            final String json = node.toString();
            responseActions.andRespond(withSuccess().body(json).contentType(MediaType.APPLICATION_JSON));
        }
        else {
            responseActions.andRespond(withException(new SocketTimeoutException()));
        }
    }
}
