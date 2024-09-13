package de.tum.cit.aet.artemis.exercise.participation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ParticipationSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "psitest"; // only lower case is supported

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = exerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Text");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteSubmissionOfParticipation() throws Exception {
        Submission submissionWithResult = participationUtilService.addSubmission(textExercise, new TextSubmission(), TEST_PREFIX + "student1");
        submissionWithResult = participationUtilService.addResultToSubmission(submissionWithResult, null);
        Long participationId = submissionWithResult.getParticipation().getId();
        Long submissionId = submissionWithResult.getId();

        // There should be a submission found by participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(1);

        request.delete("/api/submissions/" + submissionId, HttpStatus.OK);
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
        request.delete("/api/submissions/" + 1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void deleteParticipation_forbidden_tutor() throws Exception {
        request.delete("/api/submissions/" + 1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteParticipation_notFound() throws Exception {
        request.delete("/api/submissions/" + -1, HttpStatus.NOT_FOUND);
    }
}
