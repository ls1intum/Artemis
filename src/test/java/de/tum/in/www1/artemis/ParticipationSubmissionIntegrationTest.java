package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ParticipationSubmissionIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ResultRepository resultRepository;

    private TextExercise textExercise;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 2);
        database.addCourseWithOneTextExercise();
        textExercise = (TextExercise) exerciseRepo.findAll().get(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteSubmissionOfParticipation() throws Exception {
        Submission submissionWithResult = database.addSubmission(textExercise, new TextSubmission(), "student1");
        var result = database.addResultToSubmission(submissionWithResult);
        submissionWithResult.setResult(result);
        submissionRepository.save(submissionWithResult);
        Long participationId = submissionWithResult.getParticipation().getId();
        Long submissionId = submissionWithResult.getId();

        // There should be a submission found by participation.
        assertThat(submissionRepository.findByParticipationId(participationId)).hasSize(1);

        request.delete("/api/submissions/" + submissionId + "/", HttpStatus.OK);
        Optional<Submission> submission = submissionRepository.findById(submissionId);

        // Submission should now be gone.
        assertThat(submission.isPresent()).isFalse();
        // Make sure that also the submission was deleted.
        assertThat(submissionRepository.findByParticipationId(participationId)).hasSize(0);
        // Result is deleted.
        assertThat(resultRepository.findById(result.getId()).isPresent()).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void deleteParticipation_forbidden_student() throws Exception {
        request.delete("/api/submissions/" + 1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void deleteParticipation_forbidden_tutor() throws Exception {
        request.delete("/api/submissions/" + 1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteParticipation_notFound() throws Exception {
        request.delete("/api/submissions/" + 1, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void createSubmission() throws Exception {
        request.postWithResponseBody("/api/submissions", new TextSubmission(), Submission.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void createSubmission_withId() throws Exception {
        var submission = new TextSubmission();
        submission.setId(1L);
        request.postWithResponseBody("/api/submissions", submission, Submission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateSubmission() throws Exception {
        var submission = new TextSubmission();
        submissionRepository.save(submission);
        request.putWithResponseBody("/api/submissions", submission, Submission.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateSubmission_noId() throws Exception {
        var submission = new TextSubmission();
        request.putWithResponseBody("/api/submissions", submission, Submission.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getAllSubmissions() throws Exception {
        submissionRepository.save(new TextSubmission());
        submissionRepository.save(new ModelingSubmission());
        List<Submission> submissions = request.getList("/api/submissions", HttpStatus.OK, Submission.class);
        assertThat(submissions.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getSubmission() throws Exception {
        var submission = submissionRepository.save(new TextSubmission());
        request.get("/api/submissions/" + submission.getId(), HttpStatus.OK, Submission.class);
    }
}
