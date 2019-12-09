package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ProgrammingExerciseParticipationIntegrationTest {

    private final String participationsBaseUrl = "/api/programming-exercise-participations/";

    private final String exercisesBaseUrl = "/api/programming-exercises/";

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    @Autowired
    TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Autowired
    ResultRepository resultRepository;

    ProgrammingExercise programmingExercise;

    Participation programmingExerciseParticipation;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(3, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getParticipationWithLatestResultAsAStudent() throws Exception {
        addStudentParticipation();
        StudentParticipation participation = (StudentParticipation) studentParticipationRepository.findAll().get(0);
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getParticipationWithLatestResultAsAnInstructor() throws Exception {
        addStudentParticipation();
        StudentParticipation participation = (StudentParticipation) studentParticipationRepository.findAll().get(0);
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestResultWithFeedbacksAsStudent() throws Exception {
        addStudentParticipation();
        StudentParticipation participation = (StudentParticipation) studentParticipationRepository.findAll().get(0);
        request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestResultWithFeedbacksForTemplateParticipationAsTutorShouldReturnForbidden() throws Exception {
        addTemplateParticipation();
        TemplateProgrammingExerciseParticipation participation = (TemplateProgrammingExerciseParticipation) templateProgrammingExerciseParticipationRepository.findAll().get(0);
        request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getLatestResultWithFeedbacksForTemplateParticipationAsTutor() throws Exception {
        addTemplateParticipation();
        TemplateProgrammingExerciseParticipation participation = (TemplateProgrammingExerciseParticipation) templateProgrammingExerciseParticipationRepository.findAll().get(0);
        request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLatestResultWithFeedbacksForTemplateParticipationAsInstructor() throws Exception {
        addTemplateParticipation();
        TemplateProgrammingExerciseParticipation participation = (TemplateProgrammingExerciseParticipation) templateProgrammingExerciseParticipationRepository.findAll().get(0);
        request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestResultWithFeedbacksForSolutionParticipationAsTutorShouldReturnForbidden() throws Exception {
        addSolutionParticipation();
        SolutionProgrammingExerciseParticipation participation = (SolutionProgrammingExerciseParticipation) solutionProgrammingExerciseParticipationRepository.findAll().get(0);
        request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getLatestResultWithFeedbacksForSolutionParticipationAsTutor() throws Exception {
        addSolutionParticipation();
        SolutionProgrammingExerciseParticipation participation = (SolutionProgrammingExerciseParticipation) solutionProgrammingExerciseParticipationRepository.findAll().get(0);
        request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLatestResultWithFeedbacksForSolutionParticipationAsInstructor() throws Exception {
        addSolutionParticipation();
        SolutionProgrammingExerciseParticipation participation = (SolutionProgrammingExerciseParticipation) solutionProgrammingExerciseParticipationRepository.findAll().get(0);
        request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestPendingSubmissionIfExists_student() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        request.get(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getLatestPendingSubmissionIfExists_ta() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        request.get(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLatestPendingSubmissionIfExists_instructor() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        request.get(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestPendingSubmissionIfNotExists_student() throws Exception {
        // Submission has a result, therefore not considered pending.
        Result result = resultRepository.save(new Result());
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission.setResult(result);
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        Submission returnedSubmission = request.getNullable(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                ProgrammingSubmission.class);
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
        Submission returnedSubmission = request.getNullable(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                ProgrammingSubmission.class);
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
        Submission returnedSubmission = request.getNullable(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                ProgrammingSubmission.class);
        assertThat(returnedSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLatestSubmissionsForExercise_instructor() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        ProgrammingSubmission submission2 = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission2 = database.addProgrammingSubmission(programmingExercise, submission2, "student2");
        ProgrammingSubmission notPendingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(55L));
        database.addProgrammingSubmissionWithResult(programmingExercise, notPendingSubmission, "student3");
        Map<Long, ProgrammingSubmission> submissions = new HashMap<>();
        submissions.put(submission.getParticipation().getId(), submission);
        submissions.put(submission2.getParticipation().getId(), submission2);
        submissions.put(notPendingSubmission.getParticipation().getId(), null);
        Map<Long, ProgrammingSubmission> returnedSubmissions = request.getMap(exercisesBaseUrl + programmingExercise.getId() + "/latest-pending-submissions", HttpStatus.OK,
                Long.class, ProgrammingSubmission.class);
        assertThat(returnedSubmissions).isEqualTo(submissions);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestSubmissionsForExercise_studentForbidden() throws Exception {
        request.getMap(exercisesBaseUrl + programmingExercise.getId() + "/latest-pending-submissions", HttpStatus.FORBIDDEN, Long.class, ProgrammingSubmission.class);
    }

    private void addStudentParticipation() {
        programmingExerciseParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addResultToParticipation(programmingExerciseParticipation);
    }

    private void addTemplateParticipation() {
        programmingExerciseParticipation = database.addTemplateParticipationForProgrammingExercise(programmingExercise).getTemplateParticipation();
        database.addResultToParticipation(programmingExerciseParticipation);
    }

    private void addSolutionParticipation() {
        programmingExerciseParticipation = database.addSolutionParticipationForProgrammingExercise(programmingExercise).getSolutionParticipation();
        database.addResultToParticipation(programmingExerciseParticipation);
    }
}
