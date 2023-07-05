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
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
@Profile("athena")
public class AthenaRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer mockServerShortTimeout;

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    @Autowired
    private ObjectMapper mapper;

    private AutoCloseable closeable;

    public AthenaRequestMockProvider(@Qualifier("athenaRestTemplate") RestTemplate restTemplate,
            @Qualifier("shortTimeoutAthenaRestTemplate") RestTemplate shortTimeoutRestTemplate) {
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
     * Mocks /submit api from Athena used to submit new exercises for clustering.
     */
    public void mockSubmitSubmissions() {
        final ObjectNode node = mapper.createObjectNode();
        node.set("detail", mapper.valueToTree("Submission successful"));
        final String json = node.toString();

        mockServer.expect(ExpectedCount.once(), requestTo(athenaUrl + "/submit")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    /**
     * Mocks /queueStatus api from Athena used to retrieve metadata on processed jobs. Currently used as a health check endpoint.
     *
     * @param success Successful response or timeout.
     */
    public void mockQueueStatus(boolean success) {
        final ResponseActions responseActions = mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(athenaUrl + "/queueStatus")).andExpect(method(HttpMethod.GET));

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
