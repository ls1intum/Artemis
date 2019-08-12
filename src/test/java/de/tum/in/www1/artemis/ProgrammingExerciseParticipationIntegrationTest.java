package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ProgrammingExerciseParticipationIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    ResultRepository resultRepository;

    ProgrammingExercise programmingExercise;

    Participation programmingExerciseParticipation;

    @BeforeEach
    public void initTestCase() {
        database.resetDatabase();
        database.addUsers(2, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationWithLatestResultAsAStudent() throws Exception {
        addStudentParticipation();
        StudentParticipation participation = (StudentParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getParticipationWithLatestResultAsAnInstructor() throws Exception {
        addStudentParticipation();
        StudentParticipation participation = (StudentParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestResultWithFeedbacksAsStudent() throws Exception {
        addStudentParticipation();
        StudentParticipation participation = (StudentParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestResultWithFeedbacksForTemplateParticipationAsTutorShouldReturnForbidden() throws Exception {
        addTemplateParticipation();
        TemplateProgrammingExerciseParticipation participation = (TemplateProgrammingExerciseParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getLatestResultWithFeedbacksForTemplateParticipationAsTutor() throws Exception {
        addTemplateParticipation();
        TemplateProgrammingExerciseParticipation participation = (TemplateProgrammingExerciseParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLatestResultWithFeedbacksForTemplateParticipationAsInstructor() throws Exception {
        addTemplateParticipation();
        TemplateProgrammingExerciseParticipation participation = (TemplateProgrammingExerciseParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestResultWithFeedbacksForSolutionParticipationAsTutorShouldReturnForbidden() throws Exception {
        addSolutionParticipation();
        SolutionProgrammingExerciseParticipation participation = (SolutionProgrammingExerciseParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getLatestResultWithFeedbacksForSolutionParticipationAsTutor() throws Exception {
        addSolutionParticipation();
        SolutionProgrammingExerciseParticipation participation = (SolutionProgrammingExerciseParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLatestResultWithFeedbacksForSolutionParticipationAsInstructor() throws Exception {
        addSolutionParticipation();
        SolutionProgrammingExerciseParticipation participation = (SolutionProgrammingExerciseParticipation) participationRepository.findAll().get(0);
        request.get("/api/programming-exercises-participation/" + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestPendingSubmissionIfExists_student() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        request.get("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getLatestPendingSubmissionIfExists_ta() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        request.get("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLatestPendingSubmissionIfExists_instructor() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        request.get("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestPendingSubmissionIfNotExists_student() throws Exception {
        // Submission has a result, therefore not considered pending.
        Result result = resultRepository.save(new Result());
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission.setResult(result);
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        Submission returnedSubmission = request.getNullable("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission",
                HttpStatus.OK, ProgrammingSubmission.class);
        assertThat(returnedSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getLatestPendingSubmissionIfNotExists_ta() throws Exception {
        // Submission has a result, therefore not considered pending.
        Result result = resultRepository.save(new Result());
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission.setResult(result);
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        Submission returnedSubmission = request.getNullable("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission",
                HttpStatus.OK, ProgrammingSubmission.class);
        assertThat(returnedSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLatestPendingSubmissionIfNotExists_instructor() throws Exception {
        // Submission has a result, therefore not considered pending.
        Result result = resultRepository.save(new Result());
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission.setResult(result);
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        Submission returnedSubmission = request.getNullable("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission",
                HttpStatus.OK, ProgrammingSubmission.class);
        assertThat(returnedSubmission).isEqualTo(submission);
    }

    private void addStudentParticipation() {
        programmingExerciseParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addResultToParticipation(programmingExerciseParticipation);
    }

    private void addTemplateParticipation() {
        programmingExerciseParticipation = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addResultToParticipation(programmingExerciseParticipation);
    }

    private void addSolutionParticipation() {
        programmingExerciseParticipation = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addResultToParticipation(programmingExerciseParticipation);
    }
}
