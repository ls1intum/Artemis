package de.tum.cit.aet.artemis.proof;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.proof.domain.MathNodes;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;
import de.tum.cit.aet.artemis.proof.dto.ProofSubmissionDTO;
import de.tum.cit.aet.artemis.proof.util.ProofExerciseFactory;
import de.tum.cit.aet.artemis.proof.util.ProofExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ProofSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "proofsubmission";

    @Autowired
    private ProofExerciseUtilService proofExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private ProofExercise exercise;

    private StudentParticipation participation;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = proofExerciseUtilService.addCourseWithProofExercise();
        exercise = (ProofExercise) course.getExercises().iterator().next();
        participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createProofSubmission_asStudent_savesSubmission() throws Exception {
        ProofSubmissionDTO submissionDTO = ProofExerciseFactory.generateProofSubmissionDTO(false);

        ProofSubmissionDTO result = request.postWithResponseBody("/api/proof/exercises/" + exercise.getId() + "/proof-submissions", submissionDTO, ProofSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.submitted()).isFalse();
        assertThat(result.steps()).isNullOrEmpty();
        assertThat(result.results()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitProofSubmission_withValidStep_scores100() throws Exception {
        // apply add_zero_left at root: 0 + x → x (exercise source=0+x, target=x)
        var stepDTO = new ProofSubmissionDTO.DerivationStepDTO(null, 0, "add_zero_left", List.of(), MathNodes.var("x"));
        ProofSubmissionDTO submissionDTO = new ProofSubmissionDTO(null, true, null, null, null, List.of(stepDTO));

        ProofSubmissionDTO result = request.postWithResponseBody("/api/proof/exercises/" + exercise.getId() + "/proof-submissions", submissionDTO, ProofSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.submitted()).isTrue();
        assertThat(result.results()).isNotEmpty();
        assertThat(result.results().getFirst().score()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitProofSubmission_withWrongStep_scores0() throws Exception {
        // wrong rule applied: result doesn't match target
        var stepDTO = new ProofSubmissionDTO.DerivationStepDTO(null, 0, "add_zero_right", List.of(), MathNodes.var("x"));
        ProofSubmissionDTO submissionDTO = new ProofSubmissionDTO(null, true, null, null, null, List.of(stepDTO));

        ProofSubmissionDTO result = request.postWithResponseBody("/api/proof/exercises/" + exercise.getId() + "/proof-submissions", submissionDTO, ProofSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.submitted()).isTrue();
        assertThat(result.results()).isNotEmpty();
        assertThat(result.results().getFirst().score()).isEqualTo(0.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitProofSubmission_noSteps_sourceEqualsTarget_scores100() throws Exception {
        // Edge case: exercise with source == target, no steps required
        exercise.setSourceExpression(MathNodes.var("x"));
        exercise.setTargetExpression(MathNodes.var("x"));
        proofExerciseUtilService.saveExercise(exercise);

        ProofSubmissionDTO submissionDTO = new ProofSubmissionDTO(null, true, null, null, null, null);

        ProofSubmissionDTO result = request.postWithResponseBody("/api/proof/exercises/" + exercise.getId() + "/proof-submissions", submissionDTO, ProofSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.submitted()).isTrue();
        assertThat(result.results().getFirst().score()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForProofEditor_withExistingSubmission_returnsSubmission() throws Exception {
        ProofSubmission saved = proofExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        ProofSubmissionDTO result = request.get("/api/proof/participations/" + saved.getParticipation().getId() + "/proof-editor", HttpStatus.OK, ProofSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(saved.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForProofEditor_noSubmissionYet_returnsEmptySubmission() throws Exception {
        ProofSubmissionDTO result = request.get("/api/proof/participations/" + participation.getId() + "/proof-editor", HttpStatus.OK, ProofSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNull();
        assertThat(result.steps()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getProofSubmissionForAssessment_asTutor_returnsSubmissionWithParticipation() throws Exception {
        ProofSubmission saved = proofExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        ProofSubmissionDTO result = request.get("/api/proof/proof-submissions/" + saved.getId() + "/for-assessment", HttpStatus.OK, ProofSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.participation()).isNotNull();
        assertThat(result.participation().exercise()).isNotNull();
        assertThat(result.participation().exercise().id()).isEqualTo(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getProofSubmissionForAssessment_asStudent_returnsForbidden() throws Exception {
        ProofSubmission saved = proofExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        request.get("/api/proof/proof-submissions/" + saved.getId() + "/for-assessment", HttpStatus.FORBIDDEN, ProofSubmissionDTO.class);
    }
}
