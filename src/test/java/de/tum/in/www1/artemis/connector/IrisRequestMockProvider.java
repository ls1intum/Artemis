package de.tum.in.www1.artemis.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URL;
import java.util.Map;
import java.util.function.Consumer;

import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisErrorResponseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisHealthStatusDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisModelDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat.PyrisTutorChatPipelineExecutionDTO;

@Component
@Profile("iris")
public class IrisRequestMockProvider {

    private final RestTemplate restTemplate;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer mockServer;

    private MockRestServiceServer shortTimeoutMockServer;

    @Value("${artemis.iris.url}/api/v1/pipelines")
    private URL pipelinesApiURL;

    @Value("${artemis.iris.url}/api/v1/models")
    private URL modelsApiURL;

    @Value("${artemis.iris.url}/api/v1/health/")
    private URL healthApiURL;

    @Autowired
    private ObjectMapper mapper;

    private AutoCloseable closeable;

    public IrisRequestMockProvider(@Qualifier("pyrisRestTemplate") RestTemplate restTemplate, @Qualifier("shortTimeoutPyrisRestTemplate") RestTemplate shortTimeoutRestTemplate) {
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

    public void mockRunResponse(Consumer<PyrisTutorChatPipelineExecutionDTO> responseConsumer) {
        mockServer.expect(ExpectedCount.once(), requestTo(pipelinesApiURL + "/tutor-chat/default/run")).andExpect(method(HttpMethod.POST)).andRespond(request -> {
            var mockRequest = (MockClientHttpRequest) request;
            var dto = mapper.readValue(mockRequest.getBodyAsString(), PyrisTutorChatPipelineExecutionDTO.class);
            responseConsumer.accept(dto);
            return MockRestResponseCreators.withRawStatus(HttpStatus.ACCEPTED.value()).createResponse(request);
        });
    }

    public void mockRunError(int httpStatus) {
        mockServer.expect(ExpectedCount.once(), requestTo(pipelinesApiURL + "/tutor-chat/default/run")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.valueOf(httpStatus)));
    }

    public void mockCustomJsonResponse(URL requestUrl, String responseJson) {
        // @formatter:off
        mockServer.expect(ExpectedCount.once(), requestTo(requestUrl.toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
        // @formatter:on
    }

    private void mockMessageError(URL requestUrl, int status) throws JsonProcessingException {
        var json = Map.of("detail", new PyrisErrorResponseDTO("Test error"));
        // @formatter:off
        mockServer.expect(ExpectedCount.once(), requestTo(requestUrl.toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withRawStatus(status).body(mapper.writeValueAsString(json)));
        // @formatter:on
    }

    public void mockModelsResponse() throws JsonProcessingException {
        var irisModelDTO = new PyrisModelDTO("TEST_MODEL", "Test model", "Test description");
        var irisModelDTOArray = new PyrisModelDTO[] { irisModelDTO };
        // @formatter:off
        mockServer.expect(ExpectedCount.once(), requestTo(modelsApiURL.toString()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mapper.writeValueAsString(irisModelDTOArray), MediaType.APPLICATION_JSON));
        // @formatter:on
    }

    public void mockStatusResponse() throws JsonProcessingException {
        var irisStatusDTOArray = new PyrisHealthStatusDTO[] { new PyrisHealthStatusDTO("TEST_MODEL_UP", PyrisHealthStatusDTO.ModelStatus.UP),
                new PyrisHealthStatusDTO("TEST_MODEL_DOWN", PyrisHealthStatusDTO.ModelStatus.DOWN),
                new PyrisHealthStatusDTO("TEST_MODEL_NA", PyrisHealthStatusDTO.ModelStatus.NOT_AVAILABLE) };
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
