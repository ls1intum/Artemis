package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;

class ParticipationSubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(2, 2, 0, 2);
        Course course = database.addCourseWithOneReleasedTextExercise();
        textExercise = database.findTextExerciseWithTitle(course.getExercises(), "Text");
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteSubmissionOfParticipation() throws Exception {
        Submission submissionWithResult = database.addSubmission(textExercise, new TextSubmission(), "student1");
        submissionWithResult = database.addResultToSubmission(submissionWithResult, null);
        Long participationId = submissionWithResult.getParticipation().getId();
        Long submissionId = submissionWithResult.getId();

        // There should be a submission found by participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(1);

        request.delete("/api/submissions/" + submissionId + "/", HttpStatus.OK);
        Optional<Submission> submission = submissionRepository.findById(submissionId);

        // Submission should now be gone.
        assertThat(submission).isEmpty();
        // Make sure that also the submission was deleted.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).isEmpty();
        // Result is deleted.
        assertThat(resultRepository.findById(submissionWithResult.getLatestResult().getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void deleteParticipation_forbidden_student() throws Exception {
        request.delete("/api/submissions/" + 1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void deleteParticipation_forbidden_tutor() throws Exception {
        request.delete("/api/submissions/" + 1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteParticipation_notFound() throws Exception {
        request.delete("/api/submissions/" + 1, HttpStatus.NOT_FOUND);
    }
}
