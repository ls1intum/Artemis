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

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ProgrammingSubmissionIntegrationTest {

    @Autowired
    ProgrammingExerciseRepository exerciseRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    public void initTestCase() {
        database.resetDatabase();
        database.addUsers(2, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseRepo.findAll().get(0);
    }

    @Test
    @WithMockUser(username = "student1")
    public void getLatestPendingSubmissionIfExists_student() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(10L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        request.get("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void getLatestPendingSubmissionIfExists_ta() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(10L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        request.get("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "instructor1")
    public void getLatestPendingSubmissionIfExists_instructor() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(10L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        request.get("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    public void getLatestPendingSubmissionIfNotExists_student() throws Exception {
        // Submission is older than 1 minute, therefore not considered pending.
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        Submission returnedSubmission = request.getNullable("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission",
                HttpStatus.OK, ProgrammingSubmission.class);
        assertThat(returnedSubmission).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void getLatestPendingSubmissionIfNotExists_ta() throws Exception {
        // Submission is older than 1 minute, therefore not considered pending.
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        Submission returnedSubmission = request.getNullable("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission",
                HttpStatus.OK, ProgrammingSubmission.class);
        assertThat(returnedSubmission).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1")
    public void getLatestPendingSubmissionIfNotExists_instructor() throws Exception {
        // Submission is older than 1 minute, therefore not considered pending.
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addProgrammingSubmission(programmingExercise, submission, "student1");
        Submission returnedSubmission = request.getNullable("/api/programming-exercise-participation/" + submission.getParticipation().getId() + "/latest-pending-submission",
                HttpStatus.OK, ProgrammingSubmission.class);
        assertThat(returnedSubmission).isNull();
    }
}
