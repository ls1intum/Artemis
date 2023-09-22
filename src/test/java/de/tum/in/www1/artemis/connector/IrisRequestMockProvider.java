package de.tum.in.www1.artemis.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Collections;
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

import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.service.connectors.iris.dto.*;

@Component
@Profile("iris")
public class IrisRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Value("${artemis.iris.url}/api/v1/messages")
    private URL messagesApiURL;

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

    /**
     * Mocks response call for the pyris call
     */
    public void mockMessageResponse(String responseMessage) throws JsonProcessingException {
        if (responseMessage == null) {
            mockServer.expect(ExpectedCount.once(), requestTo(messagesApiURL.toString())).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
            return;
        }
        var irisMessage = new IrisMessage();
        var irisMessageContent = new IrisMessageContent();
        irisMessageContent.setTextContent(responseMessage);
        irisMessage.setContent(Collections.singletonList(irisMessageContent));
        irisMessage.setSender(IrisMessageSender.LLM);
        irisMessage.setSentAt(ZonedDateTime.now());

        var response = new IrisMessageResponseDTO(null, irisMessage);
        var json = mapper.writeValueAsString(response);

        mockServer.expect(ExpectedCount.once(), requestTo(messagesApiURL.toString())).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    public void mockCustomJsonResponse(String responseMessage) throws JsonProcessingException {
        mockServer.expect(ExpectedCount.once(), requestTo(messagesApiURL.toString())).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseMessage, MediaType.APPLICATION_JSON));
    }

    public void mockMessageError() throws JsonProcessingException {
        mockMessageError(500);
    }

    public void mockMessageError(int status) throws JsonProcessingException {
        var errorResponseDTO = new IrisErrorResponseDTO("Test error");
        var json = Map.of("detail", errorResponseDTO);
        mockServer.expect(ExpectedCount.once(), requestTo(messagesApiURL.toString())).andExpect(method(HttpMethod.POST))
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

    public void mockModelsError() throws JsonProcessingException {
        mockServer.expect(ExpectedCount.once(), requestTo(modelsApiURL.toString())).andExpect(method(HttpMethod.GET)).andRespond(withRawStatus(418));
    }
}
