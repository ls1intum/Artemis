package de.tum.cit.aet.artemis.math;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO;
import de.tum.cit.aet.artemis.math.util.MathExerciseFactory;
import de.tum.cit.aet.artemis.math.util.MathExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class MathSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "mathsubmission";

    @Autowired
    private MathExerciseUtilService mathExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private MathExercise exercise;

    private StudentParticipation participation;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = mathExerciseUtilService.addCourseWithMathExercise();
        exercise = (MathExercise) course.getExercises().iterator().next();
        participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMathSubmission_asStudent_savesSubmission() throws Exception {
        MathSubmissionDTO submissionDTO = MathExerciseFactory.generateMathSubmissionDTO(false);

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.submitted()).isFalse();
        assertThat(result.steps()).isNullOrEmpty();
        assertThat(result.results()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitMathSubmission_withValidStep_scores100() throws Exception {
        // apply add_zero_left at root: 0 + x → x (exercise source=0+x, target=x)
        var stepDTO = new MathSubmissionDTO.DerivationStepDTO(null, 0, "add_zero_left", List.of(), MathNodes.var("x"));
        MathSubmissionDTO submissionDTO = new MathSubmissionDTO(null, true, null, null, null, List.of(stepDTO));

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.submitted()).isTrue();
        assertThat(result.results()).isNotEmpty();
        assertThat(result.results().getFirst().score()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitMathSubmission_withWrongStep_scores0() throws Exception {
        // wrong rule applied: result doesn't match target
        var stepDTO = new MathSubmissionDTO.DerivationStepDTO(null, 0, "add_zero_right", List.of(), MathNodes.var("x"));
        MathSubmissionDTO submissionDTO = new MathSubmissionDTO(null, true, null, null, null, List.of(stepDTO));

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.submitted()).isTrue();
        assertThat(result.results()).isNotEmpty();
        assertThat(result.results().getFirst().score()).isEqualTo(0.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitMathSubmission_noSteps_sourceEqualsTarget_scores100() throws Exception {
        // Edge case: exercise with source == target, no steps required
        exercise.setSourceExpression(MathNodes.var("x"));
        exercise.setTargetExpression(MathNodes.var("x"));
        mathExerciseUtilService.saveExercise(exercise);

        MathSubmissionDTO submissionDTO = new MathSubmissionDTO(null, true, null, null, null, null);

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.submitted()).isTrue();
        assertThat(result.results().getFirst().score()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForMathEditor_withExistingSubmission_returnsSubmission() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        MathSubmissionDTO result = request.get("/api/math/participations/" + saved.getParticipation().getId() + "/math-editor", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(saved.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForMathEditor_noSubmissionYet_returnsEmptySubmission() throws Exception {
        MathSubmissionDTO result = request.get("/api/math/participations/" + participation.getId() + "/math-editor", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNull();
        assertThat(result.steps()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathSubmissionForAssessment_asTutor_returnsSubmissionWithParticipation() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        MathSubmissionDTO result = request.get("/api/math/math-submissions/" + saved.getId() + "/for-assessment", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.participation()).isNotNull();
        assertThat(result.participation().exercise()).isNotNull();
        assertThat(result.participation().exercise().id()).isEqualTo(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMathSubmissionForAssessment_asStudent_returnsForbidden() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        request.get("/api/math/math-submissions/" + saved.getId() + "/for-assessment", HttpStatus.FORBIDDEN, MathSubmissionDTO.class);
    }
}
