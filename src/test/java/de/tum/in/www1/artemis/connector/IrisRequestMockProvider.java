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

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.service.connectors.iris.dto.*;

@Component
@Profile("iris")
public class IrisRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

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

    public IrisRequestMockProvider(@Qualifier("irisRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
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

    public void mockEmptyResponse() {
        mockServer.expect(ExpectedCount.once(), requestTo(messagesApiV1URL.toString())).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
    }

    /**
     * Mocks a message response from the Pyris message endpoint
     */
    public void mockMessageV1Response(String responseMessage) throws JsonProcessingException {
        var irisMessage = new IrisMessage();
        irisMessage.setSender(IrisMessageSender.LLM);
        irisMessage.addContent(new IrisTextMessageContent(irisMessage, responseMessage));

        var response = new IrisMessageResponseDTO(null, irisMessage);
        var json = mapper.writeValueAsString(response);

        mockCustomJsonResponse(messagesApiV1URL, json);
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

    public void mockCustomV1Response(String json) {
        mockCustomJsonResponse(messagesApiV1URL, json);
    }

    public void mockCustomJsonResponse(URL url, String responseMessage) {
        mockServer.expect(ExpectedCount.once(), requestTo(url.toString())).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(responseMessage, MediaType.APPLICATION_JSON));
    }

    public void mockMessageV1Error(int status) throws JsonProcessingException {
        mockMessageError(messagesApiV1URL, status);
    }

    public void mockMessageV2Error(int status) throws JsonProcessingException {
        mockMessageError(messagesApiV2URL, status);
    }

    private void mockMessageError(URL requestUrl, int status) throws JsonProcessingException {
        var json = Map.of("detail", new IrisErrorResponseDTO("Test error"));
        mockServer.expect(ExpectedCount.once(), requestTo(requestUrl.toString())).andExpect(method(HttpMethod.POST))
                .andRespond(withRawStatus(status).body(mapper.writeValueAsString(json)));
    }

    public void mockModelsResponse() throws JsonProcessingException {
        var irisModelDTO = new IrisModelDTO("TEST_MODEL", "Test model", "Test description");
        var irisModelDTOArray = new IrisModelDTO[] { irisModelDTO };
        mockServer.expect(ExpectedCount.once(), requestTo(modelsApiURL.toString())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mapper.writeValueAsString(irisModelDTOArray), MediaType.APPLICATION_JSON));
    }

    public void mockStatusResponse() throws JsonProcessingException {
        var irisStatusDTOArray = new IrisStatusDTO[] { new IrisStatusDTO("TEST_MODEL_UP", IrisStatusDTO.ModelStatus.UP),
                new IrisStatusDTO("TEST_MODEL_DOWN", IrisStatusDTO.ModelStatus.DOWN), new IrisStatusDTO("TEST_MODEL_NA", IrisStatusDTO.ModelStatus.NOT_AVAILABLE) };
        mockServer.expect(ExpectedCount.once(), requestTo(healthApiURL.toString())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mapper.writeValueAsString(irisStatusDTOArray), MediaType.APPLICATION_JSON));
    }

    public void mockModelsError() {
        mockServer.expect(ExpectedCount.once(), requestTo(modelsApiURL.toString())).andExpect(method(HttpMethod.GET)).andRespond(withRawStatus(418));
    }
}
