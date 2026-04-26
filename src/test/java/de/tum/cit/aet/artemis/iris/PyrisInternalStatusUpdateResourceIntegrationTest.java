package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise.PyrisTextExerciseChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TextExerciseChatJob;

class PyrisInternalStatusUpdateResourceIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisinternalstatusupdate";

    private static final String WRONG_RUN_ID = "WRONG_RUN_ID";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private PyrisJobService pyrisJobService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        activateIrisGlobally();
    }

    private HttpHeaders createAuthHeaders(String token) {
        return new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + token))));
    }

    private List<PyrisStageDTO> terminalStages() {
        return List.of(new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Done", false));
    }

    private List<PyrisStageDTO> nonTerminalStages() {
        return List.of(new PyrisStageDTO("progress", 1, PyrisStageState.IN_PROGRESS, "Running", false));
    }

    // --- Exercise Chat (programming-exercise-chat) ---

    @Test
    void testSetStatusOfJob_runIdMismatch() throws Exception {
        String token = pyrisJobService.addExerciseChatJob(1L, 1L, 1L);
        var dto = new PyrisChatStatusUpdateDTO(null, List.of(), null, null, null, null, null);
        var headers = createAuthHeaders(token);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/pipelines/programming-exercise-chat/runs/" + WRONG_RUN_ID + "/status", dto,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match");
    }

    @Test
    void testSetStatusOfJob_invalidToken() throws Exception {
        var dto = new PyrisChatStatusUpdateDTO(null, List.of(), null, null, null, null, null);
        var headers = createAuthHeaders("invalid-token");

        request.postWithoutResponseBody("/api/iris/internal/pipelines/programming-exercise-chat/runs/someRunId/status", dto, HttpStatus.FORBIDDEN, headers);
    }

    // --- Course Chat ---

    @Test
    void testSetStatusOfCourseChatJob_runIdMismatch() throws Exception {
        String token = pyrisJobService.addCourseChatJob(1L, 1L, 1L);
        var dto = new PyrisChatStatusUpdateDTO(null, List.of(), null, null, null, null, null);
        var headers = createAuthHeaders(token);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/pipelines/course-chat/runs/" + WRONG_RUN_ID + "/status", dto, HttpStatus.CONFLICT,
                headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match");
    }

    @Test
    void testSetStatusOfCourseChatJob_invalidToken() throws Exception {
        var dto = new PyrisChatStatusUpdateDTO(null, List.of(), null, null, null, null, null);
        var headers = createAuthHeaders("invalid-token");

        request.postWithoutResponseBody("/api/iris/internal/pipelines/course-chat/runs/someRunId/status", dto, HttpStatus.FORBIDDEN, headers);
    }

    // --- Competency Extraction ---

    @Test
    void testSetCompetencyExtractionJobStatus_runIdMismatch() throws Exception {
        String token = pyrisJobService.createTokenForJob(t -> new CompetencyExtractionJob(t, 1L, 1L));
        var dto = new PyrisCompetencyStatusUpdateDTO(List.of(), List.of(), null);
        var headers = createAuthHeaders(token);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/pipelines/competency-extraction/runs/" + WRONG_RUN_ID + "/status", dto,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match");
    }

    @Test
    void testSetCompetencyExtractionJobStatus_invalidToken() throws Exception {
        var dto = new PyrisCompetencyStatusUpdateDTO(List.of(), List.of(), null);
        var headers = createAuthHeaders("invalid-token");

        request.postWithoutResponseBody("/api/iris/internal/pipelines/competency-extraction/runs/someRunId/status", dto, HttpStatus.FORBIDDEN, headers);
    }

    // --- Text Exercise Chat ---

    @Test
    void testRespondInTextExerciseChat_runIdMismatch() throws Exception {
        String token = pyrisJobService.createTokenForJob(t -> new TextExerciseChatJob(t, 1L, 1L, 1L));
        var dto = new PyrisTextExerciseChatStatusUpdateDTO(null, List.of(), null);
        var headers = createAuthHeaders(token);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/pipelines/text-exercise-chat/runs/" + WRONG_RUN_ID + "/status", dto,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match");
    }

    @Test
    void testRespondInTextExerciseChat_invalidToken() throws Exception {
        var dto = new PyrisTextExerciseChatStatusUpdateDTO(null, List.of(), null);
        var headers = createAuthHeaders("invalid-token");

        request.postWithoutResponseBody("/api/iris/internal/pipelines/text-exercise-chat/runs/someRunId/status", dto, HttpStatus.FORBIDDEN, headers);
    }

    // --- Lecture Chat ---

    @Test
    void testRespondInLectureChat_runIdMismatch() throws Exception {
        String token = pyrisJobService.addLectureChatJob(1L, 1L, 1L, 1L);
        var dto = new PyrisChatStatusUpdateDTO(null, List.of(), null, null, null, null, null);
        var headers = createAuthHeaders(token);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/pipelines/lecture-chat/runs/" + WRONG_RUN_ID + "/status", dto, HttpStatus.CONFLICT,
                headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match");
    }

    @Test
    void testRespondInLectureChat_invalidToken() throws Exception {
        var dto = new PyrisChatStatusUpdateDTO(null, List.of(), null, null, null, null, null);
        var headers = createAuthHeaders("invalid-token");

        request.postWithoutResponseBody("/api/iris/internal/pipelines/lecture-chat/runs/someRunId/status", dto, HttpStatus.FORBIDDEN, headers);
    }

    // --- Tutor Suggestion ---

    @Test
    void testSetTutorSuggestionJobStatus_runIdMismatch() throws Exception {
        String token = pyrisJobService.addTutorSuggestionJob(1L, 1L, 1L);
        var dto = new TutorSuggestionStatusUpdateDTO(null, null, List.of(), null);
        var headers = createAuthHeaders(token);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/pipelines/tutor-suggestion/runs/" + WRONG_RUN_ID + "/status", dto,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match");
    }

    @Test
    void testSetTutorSuggestionJobStatus_invalidToken() throws Exception {
        var dto = new TutorSuggestionStatusUpdateDTO(null, null, List.of(), null);
        var headers = createAuthHeaders("invalid-token");

        request.postWithoutResponseBody("/api/iris/internal/pipelines/tutor-suggestion/runs/someRunId/status", dto, HttpStatus.FORBIDDEN, headers);
    }

    // --- Lecture Ingestion ---

    @Test
    void testSetStatusOfIngestionJob_runIdMismatch() throws Exception {
        String token = pyrisJobService.addLectureIngestionWebhookJob(1L, 1L, 1L);
        String otherToken = pyrisJobService.addLectureIngestionWebhookJob(2L, 2L, 2L);
        var dto = new PyrisLectureIngestionStatusUpdateDTO(null, List.of(), 0L);
        var headers = createAuthHeaders(token);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + otherToken + "/status", dto, HttpStatus.CONFLICT,
                headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match");
    }

    @Test
    void testSetStatusOfIngestionJob_invalidToken() throws Exception {
        var dto = new PyrisLectureIngestionStatusUpdateDTO(null, List.of(), 0L);
        var headers = createAuthHeaders("invalid-token");

        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/someRunId/status", dto, HttpStatus.FORBIDDEN, headers);
    }

    @Test
    void testSetStatusOfIngestionJob_wrongJobType() throws Exception {
        String chatJobToken = pyrisJobService.addCourseChatJob(1L, 1L, 1L);
        var dto = new PyrisLectureIngestionStatusUpdateDTO(null, List.of(), 0L);
        var headers = createAuthHeaders(chatJobToken);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + chatJobToken + "/status", dto, HttpStatus.CONFLICT,
                headers);
        assertThat(response.getContentAsString()).contains("Run ID is not an ingestion job");
    }

    @Test
    void testSetStatusOfIngestionJob_terminalStages_shouldRemoveJob() throws Exception {
        String token = pyrisJobService.addLectureIngestionWebhookJob(1L, 1L, 1L);
        var dto = new PyrisLectureIngestionStatusUpdateDTO("Done", terminalStages(), 0L);
        var headers = createAuthHeaders(token);

        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + token + "/status", dto, HttpStatus.OK, headers);
        assertThat(pyrisJobService.getJob(token)).isNull();
    }

    @Test
    void testSetStatusOfIngestionJob_nonTerminalStages_shouldKeepJob() throws Exception {
        String token = pyrisJobService.addLectureIngestionWebhookJob(1L, 1L, 1L);
        var dto = new PyrisLectureIngestionStatusUpdateDTO("Running", nonTerminalStages(), 0L);
        var headers = createAuthHeaders(token);

        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + token + "/status", dto, HttpStatus.OK, headers);
        assertThat(pyrisJobService.getJob(token)).isNotNull();
    }

    // --- FAQ Ingestion ---

    @Test
    void testSetStatusOfFaqIngestionJob_runIdMismatch() throws Exception {
        String token = pyrisJobService.addFaqIngestionWebhookJob(1L, 1L);
        String otherToken = pyrisJobService.addFaqIngestionWebhookJob(2L, 2L);
        var dto = new PyrisFaqIngestionStatusUpdateDTO(null, List.of(), 0L);
        var headers = createAuthHeaders(token);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + otherToken + "/status", dto, HttpStatus.CONFLICT,
                headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match");
    }

    @Test
    void testSetStatusOfFaqIngestionJob_invalidToken() throws Exception {
        var dto = new PyrisFaqIngestionStatusUpdateDTO(null, List.of(), 0L);
        var headers = createAuthHeaders("invalid-token");

        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/someRunId/status", dto, HttpStatus.FORBIDDEN, headers);
    }

    @Test
    void testSetStatusOfFaqIngestionJob_wrongJobType() throws Exception {
        String chatJobToken = pyrisJobService.addCourseChatJob(1L, 1L, 1L);
        var dto = new PyrisFaqIngestionStatusUpdateDTO(null, List.of(), 0L);
        var headers = createAuthHeaders(chatJobToken);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + chatJobToken + "/status", dto, HttpStatus.CONFLICT,
                headers);
        assertThat(response.getContentAsString()).contains("Run ID is not an ingestion job");
    }

    @Test
    void testSetStatusOfFaqIngestionJob_terminalStages_shouldRemoveJob() throws Exception {
        String token = pyrisJobService.addFaqIngestionWebhookJob(1L, 1L);
        var dto = new PyrisFaqIngestionStatusUpdateDTO("Done", terminalStages(), 1L);
        var headers = createAuthHeaders(token);

        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + token + "/status", dto, HttpStatus.OK, headers);
        assertThat(pyrisJobService.getJob(token)).isNull();
    }

    @Test
    void testSetStatusOfFaqIngestionJob_nonTerminalStages_shouldKeepJob() throws Exception {
        String token = pyrisJobService.addFaqIngestionWebhookJob(1L, 1L);
        var dto = new PyrisFaqIngestionStatusUpdateDTO("Running", nonTerminalStages(), 1L);
        var headers = createAuthHeaders(token);

        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + token + "/status", dto, HttpStatus.OK, headers);
        assertThat(pyrisJobService.getJob(token)).isNotNull();
    }
}
