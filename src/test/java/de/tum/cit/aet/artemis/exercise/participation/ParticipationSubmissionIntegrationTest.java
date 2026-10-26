package de.tum.cit.aet.artemis.exercise.participation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.test_repository.ExampleSubmissionTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ParticipationSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "psitest"; // only lower case is supported

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ExampleSubmissionTestRepository exampleSubmissionRepository;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        textExercise = ExerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void deleteSubmissionOfParticipation() throws Exception {
        Submission submissionWithResult = participationUtilService.addSubmission(textExercise, new TextSubmission(), TEST_PREFIX + "student1");
        submissionWithResult = participationUtilService.addResultToSubmission(submissionWithResult, null);
        Long participationId = submissionWithResult.getParticipation().getId();
        Long submissionId = submissionWithResult.getId();

        // There should be a submission found by participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(1);

        request.delete("/api/exercise/submissions/" + submissionId, HttpStatus.OK);
        Optional<Submission> submission = submissionRepository.findById(submissionId);

        // Submission should now be gone.
        assertThat(submission).isEmpty();
        // Make sure that also the submission was deleted.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).isEmpty();
        // Result is deleted.
        assertThat(resultRepository.findById(submissionWithResult.getLatestResult().getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteParticipation_forbidden_student() throws Exception {
        assertSubmissionDeletionForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void deleteParticipation_forbidden_tutor() throws Exception {
        assertSubmissionDeletionForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "foreigninstructor", roles = "USER")
    void deleteSubmission_forbidden_instructorOfAnotherCourse() throws Exception {
        Course otherCourse = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Other", TEST_PREFIX + "other");
        userUtilService.addInstructorToCourse(TEST_PREFIX + "foreigninstructor", otherCourse);

        assertSubmissionDeletionForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void deleteExampleSubmissionWithoutParticipation_notFound() throws Exception {
        var example = participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission("Example text", textExercise, true));
        Submission submission = participationUtilService.addResultToSubmission(example.getSubmission(), null, textExercise.getId());
        Long submissionId = submission.getId();
        Long resultId = submission.getLatestResult().getId();
        assertThat(submission.getParticipation()).isNull();

        request.delete("/api/exercise/submissions/" + submissionId, HttpStatus.NOT_FOUND);

        var storedExample = exampleSubmissionRepository.findById(example.getId()).orElseThrow();
        assertThat(storedExample.getExercise().getId()).isEqualTo(textExercise.getId());
        assertThat(storedExample.getSubmission().getId()).isEqualTo(submissionId);
        Submission storedSubmission = submissionRepository.findWithEagerResultsAndAssessorById(submissionId).orElseThrow();
        assertThat(storedSubmission.getParticipation()).isNull();
        assertThat(storedSubmission.isExampleSubmission()).isTrue();
        assertThat(storedSubmission.getResults()).extracting(result -> result.getId()).containsExactly(resultId);
        assertThat(resultRepository.findById(resultId).orElseThrow().getSubmission().getId()).isEqualTo(submissionId);
    }

    private void assertSubmissionDeletionForbidden() throws Exception {
        Submission submission = participationUtilService.addSubmission(textExercise, new TextSubmission(), TEST_PREFIX + "student1");
        submission = participationUtilService.addResultToSubmission(submission, null);
        Long submissionId = submission.getId();
        Long participationId = submission.getParticipation().getId();
        Long resultId = submission.getLatestResult().getId();

        request.delete("/api/exercise/submissions/" + submissionId, HttpStatus.FORBIDDEN);

        Submission storedSubmission = submissionRepository.findWithEagerResultsAndAssessorById(submissionId).orElseThrow();
        assertThat(storedSubmission.getParticipation().getId()).isEqualTo(participationId);
        assertThat(storedSubmission.getResults()).extracting(result -> result.getId()).containsExactly(resultId);
        assertThat(resultRepository.findById(resultId).orElseThrow().getSubmission().getId()).isEqualTo(submissionId);
        assertThat(studentParticipationRepository.findById(participationId).orElseThrow().getExercise().getId()).isEqualTo(textExercise.getId());
        assertThat(submissionRepository.findAllByParticipationId(participationId)).extracting(Submission::getId).containsExactly(submissionId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void deleteParticipation_notFound() throws Exception {
        request.delete("/api/exercise/submissions/" + -1, HttpStatus.NOT_FOUND);
    }
}
