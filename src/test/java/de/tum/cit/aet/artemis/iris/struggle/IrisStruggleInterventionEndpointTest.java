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
import de.tum.cit.aet.artemis.iris.dto.IrisStruggleInterventionRequestDTO;
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

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student1.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        userTestRepository.save(student1);

        // The opted-out student: clear the default AI selection so hasOptedIntoLLMUsageElseThrow throws 403.
        var studentNoAi = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        studentNoAi.setSelectedLLMUsage(null);
        userTestRepository.save(studentNoAi);

        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    private long exerciseId() {
        return exercise.getId();
    }

    private IrisStruggleInterventionRequestDTO requestBody() {
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(540, "FM", List.of("FM"), 0.72, "armed", false, false),
                List.of(new PyrisStruggleSignalDTO.TickDTO(530, 0.6, 0.7)), List.of(new PyrisStruggleSignalDTO.ComponentDTO("feedbackViewing", 0.8)), 540);
        return new IrisStruggleInterventionRequestDTO(signal, Map.of("src/Sum.java", "class Sum {}"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void triggersStruggleInterventionPipeline_andReturnsAccepted() throws Exception {
        irisRequestMockProvider.mockStruggleInterventionResponse(dto -> assertThat(dto.struggleSignal()).isNotNull());

        var accepted = request.postWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", requestBody(), StruggleInterventionAcceptedDTO.class,
                HttpStatus.ACCEPTED);
        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.exerciseId()).isEqualTo(exerciseId());
        assertThat(accepted.jobId()).isNotNull();

        // executeStruggleInterventionPipeline(variant, jobToken, user, signal, exercise, submission, course, chatHistory, exerciseId)
        verify(pyrisPipelineService, timeout(3000)).executeStruggleInterventionPipeline(any(), anyString(), any(), any(), any(), any(), any(), any(), anyLong());
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
        var invalid = new IrisStruggleInterventionRequestDTO(null, Map.of("src/Sum.java", "class Sum {}"));
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention", invalid, HttpStatus.BAD_REQUEST);
    }
}
