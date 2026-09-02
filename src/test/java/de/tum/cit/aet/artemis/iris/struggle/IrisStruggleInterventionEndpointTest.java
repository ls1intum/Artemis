package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisStruggleInterventionRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionAcceptedDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Integration test for the exercise-keyed struggle-intervention trigger endpoint (Task 14, spec §5.2). The two
 * behaviors are the contract: an opted-in student gets a {@code 202 Accepted} body with {@code accepted == true}
 * and a non-null {@code jobId}, and the async pipeline fires; an opted-out student is rejected with {@code 403}
 * by the server-side AI opt-in gate (spec §10) before any pipeline work.
 */
class IrisStruggleInterventionEndpointTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "struggleendpoint";

    @Autowired
    private UserUtilService userUtilService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        // addUsers deterministically (re-run safe) seeds student1 + student2 with the course student group ("tumuser").
        // The factory defaults every generated user to CLOUD_AI, so student1 is already opted in; student2 is the
        // opt-out case below.
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);

        // The AI decision moved out of jhi_user into its own table (#13546).
        userUtilService.setAiSelectionDecision(userUtilService.getUserByLogin(TEST_PREFIX + "student1"), AiSelectionDecision.CLOUD_AI);
        // The opted-out student: addUsers records CLOUD_AI by default, so NO_AI has to be set explicitly for
        // the opt-in gate to reject them with 403.
        userUtilService.setAiSelectionDecision(userUtilService.getUserByLogin(TEST_PREFIX + "student2"), AiSelectionDecision.NO_AI);

        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        activateIrisFor(course);
        activateIrisFor(exercise);

        // activateIrisFor leaves proactive struggle OFF (the §13 default). The accepted-path test needs it ON;
        // the course-off test below flips it back off for its own case.
        setProactiveStruggleFor(course, true);
    }

    private long exerciseId() {
        return exercise.getId();
    }

    private IrisStruggleInterventionRequestDTO requestBody() {
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(540, "FM", List.of("FM"), 0.72, "armed", false, false),
                List.of(new PyrisStruggleSignalDTO.TickDTO(530, 0.6)), 540);
        return new IrisStruggleInterventionRequestDTO(signal, Map.of("src/Sum.java", "class Sum {}"), null, null, null, null, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggersStruggleInterventionPipeline_andReturnsAccepted() throws Exception {
        irisRequestMockProvider.mockStruggleInterventionResponse(dto -> assertThat(dto.struggleSignal()).isNotNull());

        var accepted = request.postWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", requestBody(), StruggleInterventionAcceptedDTO.class,
                HttpStatus.ACCEPTED);
        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.courseDisabled()).isFalse();
        assertThat(accepted.exerciseId()).isEqualTo(exerciseId());
        assertThat(accepted.jobId()).isNotNull();

        // executeStruggleInterventionPipeline(variant, supportLevel, jobToken, user, signal, exercise, submission, course, chatHistory, exerciseId, intent, episode,
        // proactivityMode)
        verify(pyrisPipelineService, timeout(3000)).executeStruggleInterventionPipeline(any(), any(), anyString(), any(), any(), any(), any(), any(), any(), anyLong(), any(),
                any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void courseProactiveDisabled_returnsAcceptedFalseCourseDisabled() throws Exception {
        var settings = irisSettingsService.getSettingsForCourse(exercise.getCourseViaExerciseGroupOrCourseMember());
        irisSettingsService.updateCourseSettings(exercise.getCourseViaExerciseGroupOrCourseMember().getId(),
                IrisCourseSettings.of(settings.enabled(), settings.customInstructions(), settings.variant(), settings.supportLevel(), settings.rateLimit(), false), true);

        var body = request.postWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", requestBody(), StruggleInterventionAcceptedDTO.class,
                HttpStatus.ACCEPTED);
        assertThat(body.accepted()).isFalse();
        assertThat(body.courseDisabled()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void optedOutUser_isForbidden() throws Exception {
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", requestBody(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void missingStruggleSignal_isBadRequest() throws Exception {
        // @Valid + @NotNull on the request body rejects a null struggleSignal synchronously (400) instead of
        // returning 202 and only failing later in the async send (which would leak the single-flight slot).
        var invalid = new IrisStruggleInterventionRequestDTO(null, Map.of("src/Sum.java", "class Sum {}"), null, null, null, null, null);
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", invalid, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void overlongEpisodeId_isBadRequest() throws Exception {
        // @Valid cascade + @Size(max=64) on the episode id rejects a client-supplied id wider than the
        // proactive_episode_id column synchronously (400), before the single-flight slot is reserved.
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(540, "FM", List.of("FM"), 0.72, "armed", false, false),
                List.of(new PyrisStruggleSignalDTO.TickDTO(530, 0.6)), 540);
        var episode = new StruggleEpisodeDTO("e".repeat(65), true, List.of());
        var invalid = new IrisStruggleInterventionRequestDTO(signal, Map.of("src/Sum.java", "class Sum {}"), null, episode, null, null, null);
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", invalid, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void blankEpisodeId_isBadRequest() throws Exception {
        // A blank id passes @Size but would be persisted verbatim by an active decision and then key the
        // terminal-outcome gate: once one blank-id episode ended, every later blank-id intervention for this student
        // would be read as that same finished episode and suppressed. Reject it at the boundary (400) instead.
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(540, "FM", List.of("FM"), 0.72, "armed", false, false),
                List.of(new PyrisStruggleSignalDTO.TickDTO(530, 0.6)), 540);
        var episode = new StruggleEpisodeDTO("   ", true, List.of());
        var invalid = new IrisStruggleInterventionRequestDTO(signal, Map.of("src/Sum.java", "class Sum {}"), null, episode, null, null, null);
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", invalid, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void emptyEpisodeId_isBadRequest() throws Exception {
        // Same rejection for the empty string, the shape a client bug produces most easily.
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(540, "FM", List.of("FM"), 0.72, "armed", false, false),
                List.of(new PyrisStruggleSignalDTO.TickDTO(530, 0.6)), 540);
        var episode = new StruggleEpisodeDTO("", true, List.of());
        var invalid = new IrisStruggleInterventionRequestDTO(signal, Map.of("src/Sum.java", "class Sum {}"), null, episode, null, null, null);
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", invalid, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void episodeWithoutId_isStillAccepted() throws Exception {
        // The episode object stays optional as a whole: a null id keeps the legacy no-episode behaviour and must not
        // be swept up by the blank rejection above.
        irisRequestMockProvider.mockStruggleInterventionResponse(dto -> assertThat(dto.struggleSignal()).isNotNull());

        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(540, "FM", List.of("FM"), 0.72, "armed", false, false),
                List.of(new PyrisStruggleSignalDTO.TickDTO(530, 0.6)), 540);
        var episode = new StruggleEpisodeDTO(null, true, List.of());
        var body = new IrisStruggleInterventionRequestDTO(signal, Map.of("src/Sum.java", "class Sum {}"), null, episode, null, null, null);

        var accepted = request.postWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", body, StruggleInterventionAcceptedDTO.class,
                HttpStatus.ACCEPTED);
        assertThat(accepted.accepted()).isTrue();
    }
}
