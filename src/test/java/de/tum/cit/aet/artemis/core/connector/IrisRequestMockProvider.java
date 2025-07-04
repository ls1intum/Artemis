package de.tum.cit.aet.artemis.core.connector;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.context.annotation.Lazy;
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

import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisHealthStatusDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisVariantDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.course.PyrisCourseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.exercise.PyrisExerciseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture.PyrisLectureChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise.PyrisTextExerciseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.tutorsuggestion.PyrisTutorSuggestionPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyExtractionPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.consistencyCheck.PyrisConsistencyCheckPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.PyrisRewritingPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcriptionIngestion.PyrisWebhookTranscriptionDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcriptionIngestion.PyrisWebhookTranscriptionIngestionExecutionDTO;

@Component
@Profile(PROFILE_IRIS)
@Lazy
public class IrisRequestMockProvider {

    private final RestTemplate restTemplate;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer mockServer;

    private MockRestServiceServer shortTimeoutMockServer;

    @Value("${artemis.iris.url}/api/v1/pipelines")
    private URL pipelinesApiURL;

    @Value("${artemis.iris.url}/api/v1/webhooks")
    private URL webhooksApiURL;

    @Value("${artemis.iris.url}/api/v1/pipelines/")
    private String variantsApiBaseURL;

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

    public void mockProgrammingExerciseChatResponse(Consumer<PyrisExerciseChatPipelineExecutionDTO> responseConsumer) {
        mockPostRequest("/programming-exercise-chat/run", PyrisExerciseChatPipelineExecutionDTO.class, responseConsumer);
    }

    public void mockProgrammingExerciseChatResponseExpectingSubmissionId(Consumer<PyrisExerciseChatPipelineExecutionDTO> responseConsumer, long submissionId) {
        // @formatter:off
        mockServer
            .expect(ExpectedCount.once(), requestTo(pipelinesApiURL + "/programming-exercise-chat/run"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(request -> {
                var mockRequest = (MockClientHttpRequest) request;
                var jsonNode = mapper.readTree(mockRequest.getBodyAsString());

                assertThat(jsonNode.has("submission"))
                    .withFailMessage("Request body must contain a 'submission' field")
                    .isTrue();
                assertThat(jsonNode.get("submission").isObject())
                    .withFailMessage("The 'submission' field must be an object")
                    .isTrue();
                assertThat(jsonNode.get("submission").has("id"))
                    .withFailMessage("The 'submission' object must contain an 'id' field")
                    .isTrue();
                assertThat(jsonNode.get("submission").get("id").asLong())
                    .withFailMessage("Submission ID in request (%d) does not match expected ID (%d)",
                        jsonNode.get("submission").get("id").asLong(), submissionId)
                    .isEqualTo(submissionId);
            })
            .andRespond(request -> {
                var mockRequest = (MockClientHttpRequest) request;
                var dto = mapper.readValue(mockRequest.getBodyAsString(), PyrisExerciseChatPipelineExecutionDTO.class);
                responseConsumer.accept(dto);
                return MockRestResponseCreators.withRawStatus(HttpStatus.ACCEPTED.value()).createResponse(request);
            });
        // @formatter:on
    }

    public void mockTextExerciseChatResponse(Consumer<PyrisTextExerciseChatPipelineExecutionDTO> responseConsumer) {
        mockPostRequest("/text-exercise-chat/run", PyrisTextExerciseChatPipelineExecutionDTO.class, responseConsumer);
    }

    public void mockLectureChatResponse(Consumer<PyrisLectureChatPipelineExecutionDTO> responseConsumer) {
        mockPostRequest("/lecture-chat/run", PyrisLectureChatPipelineExecutionDTO.class, responseConsumer);
    }

    public void mockTutorSuggestionResponse(Consumer<PyrisTutorSuggestionPipelineExecutionDTO> responseConsumer) {
        mockPostRequest("/tutor-suggestion/run", PyrisTutorSuggestionPipelineExecutionDTO.class, responseConsumer);
    }

    public void mockRunCompetencyExtractionResponseAnd(Consumer<PyrisCompetencyExtractionPipelineExecutionDTO> responseConsumer) {
        mockPostRequest("/competency-extraction/run", PyrisCompetencyExtractionPipelineExecutionDTO.class, responseConsumer);
    }

    public void mockRewritingPipelineResponse(Consumer<PyrisRewritingPipelineExecutionDTO> responseConsumer) {
        mockPostRequest("/rewriting/run", PyrisRewritingPipelineExecutionDTO.class, responseConsumer);
    }

    public void mockProgrammingConsistencyCheckResponse(Consumer<PyrisConsistencyCheckPipelineExecutionDTO> responseConsumer) {
        mockPostRequest("/inconsistency-check/run", PyrisConsistencyCheckPipelineExecutionDTO.class, responseConsumer);
    }

    public void mockIngestionWebhookRunResponse(Consumer<PyrisWebhookLectureIngestionExecutionDTO> responseConsumer) {
        mockWebhookPost("/lectures/ingest", PyrisWebhookLectureIngestionExecutionDTO.class, responseConsumer);
    }

    public void mockTranscriptionIngestionWebhookRunResponse(Consumer<PyrisWebhookTranscriptionIngestionExecutionDTO> responseConsumer) {
        mockWebhookPost("/transcriptions/ingest", PyrisWebhookTranscriptionIngestionExecutionDTO.class, responseConsumer);
    }

    public void mockTranscriptionDeletionWebhookRunResponse(Consumer<PyrisWebhookTranscriptionDeletionExecutionDTO> responseConsumer) {
        mockWebhookPost("/transcriptions/delete", PyrisWebhookTranscriptionDeletionExecutionDTO.class, responseConsumer);
    }

    public void mockFaqIngestionWebhookRunResponse(Consumer<PyrisWebhookFaqIngestionExecutionDTO> responseConsumer) {
        mockWebhookPost("/faqs/ingest", PyrisWebhookFaqIngestionExecutionDTO.class, responseConsumer);
    }

    public void mockDeletionWebhookRunResponse(Consumer<PyrisWebhookLectureIngestionExecutionDTO> responseConsumer) {
        mockWebhookPost("/lectures/delete", PyrisWebhookLectureIngestionExecutionDTO.class, responseConsumer);
    }

    public void mockFaqDeletionWebhookRunResponse(Consumer<PyrisWebhookFaqIngestionExecutionDTO> responseConsumer) {
        mockWebhookPost("/faqs/delete", PyrisWebhookFaqIngestionExecutionDTO.class, responseConsumer);
    }

    public void mockBuildFailedRunResponse(Consumer<PyrisExerciseChatPipelineExecutionDTO> responseConsumer) {
        mockPostRequest("/programming-exercise-chat/run?event=build_failed", PyrisExerciseChatPipelineExecutionDTO.class, responseConsumer, ExpectedCount.max(2));
    }

    public void mockProgressStalledEventRunResponse(Consumer<PyrisCourseChatPipelineExecutionDTO> responseConsumer) {
        mockPostRequest("/programming-exercise-chat/run?event=progress_stalled", PyrisCourseChatPipelineExecutionDTO.class, responseConsumer, ExpectedCount.max(2));
    }

    public void mockJolEventRunResponse(Consumer<PyrisCourseChatPipelineExecutionDTO> responseConsumer) {
        mockPostRequest("/course-chat/run?event=jol", PyrisCourseChatPipelineExecutionDTO.class, responseConsumer);
    }

    public void mockRunError(int httpStatus) {
        mockPostError(pipelinesApiURL.toString(), "/programming-exercise-chat/run", httpStatus);
    }

    public void mockIngestionWebhookRunError(int httpStatus) {
        mockPostError(webhooksApiURL.toString(), "/lectures/ingest", httpStatus);
    }

    public void mockDeletionWebhookRunError(int httpStatus) {
        mockPostError(webhooksApiURL.toString(), "/lectures/delete", httpStatus);
    }

    public void mockVariantsResponse(IrisSubSettingsType feature) throws JsonProcessingException {
        var irisModelDTO = new PyrisVariantDTO("TEST_MODEL", "Test model", "Test description");
        var irisModelDTOArray = new PyrisVariantDTO[] { irisModelDTO };
        // @formatter:off
        mockServer.expect(ExpectedCount.once(), requestTo(variantsApiBaseURL + feature.name() + "/variants"))
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

    private void mockPostError(String baseUrl, String path, int httpStatus) {
        mockServer.expect(ExpectedCount.once(), requestTo(baseUrl + path)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.valueOf(httpStatus)));
    }

    private <T> void mockPostRequest(String path, Class<T> dtoClass, Consumer<T> responseConsumer) {
        mockPostRequest(path, dtoClass, responseConsumer, ExpectedCount.once());
    }

    private <T> void mockPostRequest(String path, Class<T> dtoClass, Consumer<T> responseConsumer, ExpectedCount count) {
        mockServer.expect(count, requestTo(pipelinesApiURL + path)).andExpect(method(HttpMethod.POST)).andRespond(request -> {
            var mockRequest = (MockClientHttpRequest) request;
            var dto = mapper.readValue(mockRequest.getBodyAsString(), dtoClass);
            responseConsumer.accept(dto);
            return MockRestResponseCreators.withRawStatus(HttpStatus.ACCEPTED.value()).createResponse(request);
        });
    }

    private <T> void mockWebhookPost(String path, Class<T> dtoClass, Consumer<T> responseConsumer) {
        mockServer.expect(ExpectedCount.once(), requestTo(webhooksApiURL + path)).andExpect(method(HttpMethod.POST)).andRespond(request -> {
            var mockRequest = (MockClientHttpRequest) request;
            var dto = mapper.readValue(mockRequest.getBodyAsString(), dtoClass);
            responseConsumer.accept(dto);
            return MockRestResponseCreators.withRawStatus(HttpStatus.ACCEPTED.value()).createResponse(request);
        });
    }

    /**
     * Mocks a get model error from the Pyris models endpoint
     */
    public void mockVariantsError(IrisSubSettingsType feature) {
        // @formatter:off
        mockServer.expect(ExpectedCount.once(), requestTo(variantsApiBaseURL + feature.name() + "/variants"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withRawStatus(418));
        // @formatter:on
    }
}
