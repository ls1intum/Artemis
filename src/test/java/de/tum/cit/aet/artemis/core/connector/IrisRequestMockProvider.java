package de.tum.cit.aet.artemis.core.connector;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URL;
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

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisHealthStatusDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisModelDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.exercise.PyrisExerciseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyExtractionPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;

@Component
@Profile(PROFILE_IRIS)
public class IrisRequestMockProvider {

    private final RestTemplate restTemplate;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer mockServer;

    private MockRestServiceServer shortTimeoutMockServer;

    @Value("${artemis.iris.url}/api/v1/pipelines")
    private URL pipelinesApiURL;

    @Value("${artemis.iris.url}/api/v1/webhooks")
    private URL webhooksApiURL;

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

    public void mockRunResponse(Consumer<PyrisExerciseChatPipelineExecutionDTO> responseConsumer) {
        // @formatter:off
        mockServer
            .expect(ExpectedCount.once(), requestTo(pipelinesApiURL + "/tutor-chat/default/run"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(request -> {
                var mockRequest = (MockClientHttpRequest) request;
                var dto = mapper.readValue(mockRequest.getBodyAsString(), PyrisExerciseChatPipelineExecutionDTO.class);
                responseConsumer.accept(dto);
                return MockRestResponseCreators.withRawStatus(HttpStatus.ACCEPTED.value()).createResponse(request);
            });
        // @formatter:on
    }

    public void mockRunCompetencyExtractionResponseAnd(Consumer<PyrisCompetencyExtractionPipelineExecutionDTO> executionDTOConsumer) {
        // @formatter:off
        mockServer
            .expect(ExpectedCount.once(), requestTo(pipelinesApiURL + "/competency-extraction/default/run"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(request -> {
                var mockRequest = (MockClientHttpRequest) request;
                var dto = mapper.readValue(mockRequest.getBodyAsString(), PyrisCompetencyExtractionPipelineExecutionDTO.class);
                executionDTOConsumer.accept(dto);
                return MockRestResponseCreators.withRawStatus(HttpStatus.ACCEPTED.value()).createResponse(request);
            });
        // @formatter:on
    }

    public void mockIngestionWebhookRunResponse(Consumer<PyrisWebhookLectureIngestionExecutionDTO> responseConsumer) {
        mockServer.expect(ExpectedCount.once(), requestTo(webhooksApiURL + "/lectures/fullIngestion")).andExpect(method(HttpMethod.POST)).andRespond(request -> {
            var mockRequest = (MockClientHttpRequest) request;
            var dto = mapper.readValue(mockRequest.getBodyAsString(), PyrisWebhookLectureIngestionExecutionDTO.class);
            responseConsumer.accept(dto);
            return MockRestResponseCreators.withRawStatus(HttpStatus.ACCEPTED.value()).createResponse(request);
        });
    }

    public void mockRunError(int httpStatus) {
        // @formatter:off
        mockServer
            .expect(ExpectedCount.once(), requestTo(pipelinesApiURL + "/tutor-chat/default/run"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.valueOf(httpStatus)));
        // @formatter:on
    }

    public void mockIngestionWebhookRunError(int httpStatus) {
        // @formatter:off
        mockServer
            .expect(ExpectedCount.once(), requestTo(webhooksApiURL + "/lectures/fullIngestion"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.valueOf(httpStatus)));
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

    public void mockStatusResponses() throws JsonProcessingException {
        // @formatter:off
        PyrisHealthStatusDTO[] activeIrisStatusDTO = new PyrisHealthStatusDTO[] {
            new PyrisHealthStatusDTO("model", PyrisHealthStatusDTO.ModelStatus.UP)
        };

        shortTimeoutMockServer
            .expect(ExpectedCount.once(), requestTo(healthApiURL.toString()))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(mapper.writeValueAsString(activeIrisStatusDTO), MediaType.APPLICATION_JSON));
        shortTimeoutMockServer
            .expect(ExpectedCount.once(), requestTo(healthApiURL.toString()))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(mapper.writeValueAsString(null), MediaType.APPLICATION_JSON));
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
