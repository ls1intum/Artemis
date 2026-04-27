package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.iris.dto.IrisGlobalSearchAnswerWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisSearchAskRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;

class IrisLectureSearchIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "lecturesearchit";

    @BeforeEach
    void setupUsers() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        activateIrisGlobally();
    }

    // ==================== /api/iris/lecture-search (synchronous) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_shouldReturnResults() throws Exception {
        var results = List.of(
                new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(5L, "Machine Learning"), new PyrisLectureSearchResultDTO.LectureDTO(10L, "Intro to ML"),
                        new PyrisLectureSearchResultDTO.LectureUnitDTO(1L, "Introduction Slide", "/link/1", 3), "supervised learning snippet"),
                new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(5L, "Machine Learning"), new PyrisLectureSearchResultDTO.LectureDTO(10L, "Intro to ML"),
                        new PyrisLectureSearchResultDTO.LectureUnitDTO(2L, "Neural Networks", "/link/2", 7), "backpropagation snippet"));
        irisRequestMockProvider.mockSearchLectures(results);

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).lectureUnit().id()).isEqualTo(1L);
        assertThat(response.get(0).snippet()).isEqualTo("supervised learning snippet");
        assertThat(response.get(1).lectureUnit().id()).isEqualTo(2L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_shouldReturnEmptyList() throws Exception {
        irisRequestMockProvider.mockSearchLectures(List.of());

        var requestDTO = new PyrisLectureSearchRequestDTO("nonexistent topic", 5);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        irisRequestMockProvider.mockSearchLecturesError(HttpStatus.INTERNAL_SERVER_ERROR);

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void search_asUnauthenticated_shouldReturnUnauthorized() throws Exception {
        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.UNAUTHORIZED);
    }

    // ==================== /api/iris/search-answer (async, webhook-based) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_shouldReturnAccepted() throws Exception {
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> {
            // no assertions needed here; just confirm the mock is consumed
        });

        var requestDTO = new PyrisSearchAskRequestDTO("What is backpropagation?", 5);
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_thinkingWebhook_shouldForwardThinkingToWebSocket() throws Exception {
        AtomicReference<String> jobIdRef = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> jobIdRef.set(dto.settings().authenticationToken()));

        var requestDTO = new PyrisSearchAskRequestDTO("What is backpropagation?", 5);
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);

        var thinkingStage = new PyrisStageDTO("Classifying query", 10, PyrisStageState.IN_PROGRESS, null, false, null);
        sendLectureSearchStatus(jobIdRef.get(), new PyrisGlobalSearchAnswerStatusUpdateDTO(List.of(thinkingStage), null, null));

        verifyMessageWasSentOverWebsocket(TEST_PREFIX + "student1", "lecture-search",
                obj -> obj instanceof IrisGlobalSearchAnswerWebsocketDTO dto && dto.isThinking() && dto.answer() == null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_resultWebhookWithAnswer_shouldForwardAnswerToWebSocket() throws Exception {
        AtomicReference<String> jobIdRef = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> jobIdRef.set(dto.settings().authenticationToken()));

        var requestDTO = new PyrisSearchAskRequestDTO("What is backpropagation?", 5);
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);

        var source = new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(1L, "ML"), new PyrisLectureSearchResultDTO.LectureDTO(2L, "Intro"),
                new PyrisLectureSearchResultDTO.LectureUnitDTO(3L, "Neural Nets", "/link/3", 5), "backprop snippet");
        var doneStage = new PyrisStageDTO("LLM", 90, PyrisStageState.DONE, null, false, null);
        sendLectureSearchStatus(jobIdRef.get(), new PyrisGlobalSearchAnswerStatusUpdateDTO(List.of(doneStage), "Neural networks learn via backpropagation.", List.of(source)));

        verifyMessageWasSentOverWebsocket(TEST_PREFIX + "student1", "lecture-search",
                obj -> obj instanceof IrisGlobalSearchAnswerWebsocketDTO dto && !dto.isThinking() && "Neural networks learn via backpropagation.".equals(dto.answer()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_resultWebhookWithNullAnswer_shouldSendCompletionWithNoAnswer() throws Exception {
        AtomicReference<String> jobIdRef = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> jobIdRef.set(dto.settings().authenticationToken()));

        var requestDTO = new PyrisSearchAskRequestDTO("Go to course overview", 5);
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);

        var doneStage = new PyrisStageDTO("Classifying query", 10, PyrisStageState.DONE, null, false, null);
        sendLectureSearchStatus(jobIdRef.get(), new PyrisGlobalSearchAnswerStatusUpdateDTO(List.of(doneStage), null, null));

        verifyMessageWasSentOverWebsocket(TEST_PREFIX + "student1", "lecture-search",
                obj -> obj instanceof IrisGlobalSearchAnswerWebsocketDTO dto && !dto.isThinking() && dto.answer() == null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        irisRequestMockProvider.mockGlobalSearchIrisAnswerError(HttpStatus.INTERNAL_SERVER_ERROR);

        var requestDTO = new PyrisSearchAskRequestDTO("machine learning", 5);
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void ask_asUnauthenticated_shouldReturnUnauthorized() throws Exception {
        var requestDTO = new PyrisSearchAskRequestDTO("machine learning", 5);
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.UNAUTHORIZED);
    }

    // ==================== helpers ====================

    private void sendLectureSearchStatus(String jobId, PyrisGlobalSearchAnswerStatusUpdateDTO statusUpdate) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobId))));
        request.postWithoutResponseBody("/api/iris/internal/pipelines/global-search/runs/" + jobId + "/status", statusUpdate, HttpStatus.OK, headers);
    }
}
