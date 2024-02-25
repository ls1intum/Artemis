package de.tum.in.www1.artemis.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Map;

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
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.service.connectors.iris.dto.*;

@Component
@Profile("iris")
public class IrisRequestMockProvider {

    private final RestTemplate restTemplate;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer mockServer;

    private MockRestServiceServer shortTimeoutMockServer;

    @Value("${artemis.iris.url}/api/v1/messages")
    private URL messagesApiV1URL;

    @Value("${artemis.iris.url}/api/v2/messages")
    private URL messagesApiV2URL;

    @Value("${artemis.iris.url}/api/v1/models")
    private URL modelsApiURL;

    @Value("${artemis.iris.url}/api/v1/health")
    private URL healthApiURL;

    @Autowired
    private ObjectMapper mapper;

    private AutoCloseable closeable;

    public IrisRequestMockProvider(@Qualifier("irisRestTemplate") RestTemplate restTemplate, @Qualifier("shortTimeoutIrisRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        shortTimeoutMockServer = MockRestServiceServer.createServer(shortTimeoutRestTemplate);
        closeable = MockitoAnnotations.openMocks(this);
    }

    public void reset() throws Exception {
        if (mockServer != null) {
            mockServer.reset();
        }

        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Mocks a message with empty response from the Pyris message endpoint
     */
    public void mockEmptyResponse() {
        // @formatter:off
        mockServer.expect(ExpectedCount.once(), requestTo(messagesApiV1URL.toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        // @formatter:on
    }

    /**
     * Mocks a message response from the Pyris V2 message endpoint
     *
     * @param responseContent The content of the response
     * @throws JsonProcessingException If the response content cannot be serialized to JSON
     */
    public void mockMessageV2Response(Map<?, ?> responseContent) throws JsonProcessingException {
        var dto = new IrisMessageResponseV2DTO(null, ZonedDateTime.now(), mapper.valueToTree(responseContent));
        var json = mapper.writeValueAsString(dto);

        mockCustomJsonResponse(messagesApiV2URL, json);
    }

    public void mockCustomJsonResponse(URL requestUrl, String responseJson) {
        // @formatter:off
        mockServer.expect(ExpectedCount.once(), requestTo(requestUrl.toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
        // @formatter:on
    }

    public void mockMessageV1Error(int status) throws JsonProcessingException {
        mockMessageError(messagesApiV1URL, status);
    }

    public void mockMessageV2Error(int status) throws JsonProcessingException {
        mockMessageError(messagesApiV2URL, status);
    }

    private void mockMessageError(URL requestUrl, int status) throws JsonProcessingException {
        var json = Map.of("detail", new IrisErrorResponseDTO("Test error"));
        // @formatter:off
        mockServer.expect(ExpectedCount.once(), requestTo(requestUrl.toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withRawStatus(status).body(mapper.writeValueAsString(json)));
        // @formatter:on
    }

    public void mockModelsResponse() throws JsonProcessingException {
        var irisModelDTO = new IrisModelDTO("TEST_MODEL", "Test model", "Test description");
        var irisModelDTOArray = new IrisModelDTO[] { irisModelDTO };
        // @formatter:off
        mockServer.expect(ExpectedCount.once(), requestTo(modelsApiURL.toString()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mapper.writeValueAsString(irisModelDTOArray), MediaType.APPLICATION_JSON));
        // @formatter:on
    }

    public void mockStatusResponse() throws JsonProcessingException {
        var irisStatusDTOArray = new IrisStatusDTO[] { new IrisStatusDTO("TEST_MODEL_UP", IrisStatusDTO.ModelStatus.UP),
                new IrisStatusDTO("TEST_MODEL_DOWN", IrisStatusDTO.ModelStatus.DOWN), new IrisStatusDTO("TEST_MODEL_NA", IrisStatusDTO.ModelStatus.NOT_AVAILABLE) };
        // @formatter:off
        shortTimeoutMockServer.expect(ExpectedCount.once(), requestTo(healthApiURL.toString()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mapper.writeValueAsString(irisStatusDTOArray), MediaType.APPLICATION_JSON));
        // @formatter:on
    }

    /**
     * Mocks a get model error from the Pyris models endpoint
     */
    public void mockModelsError() {
        // @formatter:off
        mockServer.expect(ExpectedCount.once(), requestTo(modelsApiURL.toString()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withRawStatus(418));
        // @formatter:on
    }
}
