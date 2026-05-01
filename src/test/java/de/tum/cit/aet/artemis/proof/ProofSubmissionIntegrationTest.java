package de.tum.cit.aet.artemis.proof;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;
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
        ProofSubmission submission = ProofExerciseFactory.generateProofSubmission(false);

        ProofSubmission result = request.postWithResponseBody(
                "/api/proof/exercises/" + exercise.getId() + "/proof-submissions", submission, ProofSubmission.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getText()).isEqualTo(submission.getText());
        assertThat(result.isStudentCheckboxState()).isEqualTo(submission.isStudentCheckboxState());
        assertThat(result.isSubmitted()).isFalse();
        assertThat(result.getResults()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitProofSubmission_checkboxMatchesPredefined_scores100() throws Exception {
        // exercise.predefinedCheckboxState = true (set in factory), submission.studentCheckboxState = true
        ProofSubmission submission = ProofExerciseFactory.generateProofSubmission(true);
        submission.setStudentCheckboxState(true);

        ProofSubmission result = request.postWithResponseBody(
                "/api/proof/exercises/" + exercise.getId() + "/proof-submissions", submission, ProofSubmission.class, HttpStatus.OK);

        assertThat(result.isSubmitted()).isTrue();
        assertThat(result.getResults()).isNotEmpty();
        assertThat(result.getResults().getFirst().getScore()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitProofSubmission_checkboxMismatch_scores0() throws Exception {
        // exercise.predefinedCheckboxState = true, submission.studentCheckboxState = false → mismatch
        ProofSubmission submission = ProofExerciseFactory.generateProofSubmission(true);
        submission.setStudentCheckboxState(false);

        ProofSubmission result = request.postWithResponseBody(
                "/api/proof/exercises/" + exercise.getId() + "/proof-submissions", submission, ProofSubmission.class, HttpStatus.OK);

        assertThat(result.isSubmitted()).isTrue();
        assertThat(result.getResults()).isNotEmpty();
        assertThat(result.getResults().getFirst().getScore()).isEqualTo(0.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForProofEditor_withExistingSubmission_returnsSubmission() throws Exception {
        ProofSubmission saved = proofExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        ProofSubmission result = request.get(
                "/api/proof/participations/" + saved.getParticipation().getId() + "/proof-editor", HttpStatus.OK, ProofSubmission.class);

        assertThat(result).isNotNull();
        assertThat(result.getText()).isEqualTo(saved.getText());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForProofEditor_noSubmissionYet_returnsEmptySubmission() throws Exception {
        ProofSubmission result = request.get(
                "/api/proof/participations/" + participation.getId() + "/proof-editor", HttpStatus.OK, ProofSubmission.class);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getText()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getProofSubmissionForAssessment_asTutor_returnsSubmissionWithParticipation() throws Exception {
        ProofSubmission saved = proofExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        ProofSubmission result = request.get(
                "/api/proof/proof-submissions/" + saved.getId() + "/for-assessment", HttpStatus.OK, ProofSubmission.class);

        assertThat(result).isNotNull();
        assertThat(result.getParticipation()).isNotNull();
        assertThat(result.getParticipation().getExercise()).isNotNull();
        assertThat(result.getParticipation().getExercise().getId()).isEqualTo(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getProofSubmissionForAssessment_asStudent_returnsForbidden() throws Exception {
        ProofSubmission saved = proofExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        request.get("/api/proof/proof-submissions/" + saved.getId() + "/for-assessment", HttpStatus.FORBIDDEN, ProofSubmission.class);
    }
}
