package de.tum.in.www1.artemis.connector.apollon;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Profile("apollon")
public class ApollonRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer mockServerShortTimeout;

    @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl;

    @Autowired
    private ObjectMapper mapper;

    public ApollonRequestMockProvider(@Qualifier("apollonRestTemplate") RestTemplate restTemplate,
            @Qualifier("shortTimeoutApollonRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServerShortTimeout = MockRestServiceServer.createServer(shortTimeoutRestTemplate);
        MockitoAnnotations.openMocks(this);
    }

    public void reset() {
        if (mockServer != null) {
            mockServer.reset();
        }

        if (mockServerShortTimeout != null) {
            mockServerShortTimeout.reset();
        }
    }

    /**
     * Mocks /status api from Apollon used to retrieve meta data on processed jobs. Currently used as a health check endpoint.
     *
     * @param success Successful response or timeout.
     */
    public void mockStatus(boolean success) {
        final ResponseActions responseActions = mockServerShortTimeout.expect(ExpectedCount.once(), requestTo(apollonConversionUrl + "/status")).andExpect(method(HttpMethod.GET));

        if (success) {
            responseActions.andRespond(withSuccess());
        }
        else {
            responseActions.andRespond(withException(new SocketTimeoutException()));
        }
    }

    /**
     * Mocks /pdf api from Apollon used to retrieve meta data on processed jobs. Currently used as a health check endpoint.
     *
     * @param success Successful response or timeout.
     * @param resource Resource that will be returned by the server
     */
    public void mockConvertModel(boolean success, Resource resource) {
        final ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(apollonConversionUrl + "/pdf")).andExpect(method(HttpMethod.POST));

        if (success) {
            responseActions.andRespond(withSuccess().body(resource));
        }
        else {
            responseActions.andRespond(withException(new IOException()));
        }
    }
}
