package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
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
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ParticipationIntegrationTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    StudentParticipationRepository participationRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    private Course course;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    @BeforeEach
    public void initTestCase() {
        database.resetDatabase();
        database.addUsers(2, 2, 2);
        database.addCourseWithModelingAndTextExercise();
        course = courseRepo.findAll().get(0);
        modelingExercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        textExercise = (TextExercise) exerciseRepo.findAll().get(1);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateInModelingExercise() throws Exception {
        URI location = request.post("/api/courses/" + course.getId() + "/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);

        StudentParticipation participation = request.get(location.getPath(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("participated in correct exercise").isEqualTo(modelingExercise);
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getStudent().getLogin()).as("Correct student got set").isEqualTo("student1");
        Participation storedParticipation = participationRepo.findWithEagerSubmissionsByExerciseIdAndStudentLogin(modelingExercise.getId(), "student1").get();
        assertThat(storedParticipation.getSubmissions().size()).as("submission was initialized").isEqualTo(1);
        assertThat(storedParticipation.getSubmissions().iterator().next().getClass()).as("submission is of type modeling submission").isEqualTo(ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student2")
    public void participateInTextExercise() throws Exception {
        URI location = request.post("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);

        StudentParticipation participation = request.get(location.getPath(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("participated in correct exercise").isEqualTo(textExercise);
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getStudent().getLogin()).as("Correct student got set").isEqualTo("student2");
        Participation storedParticipation = participationRepo.findWithEagerSubmissionsByExerciseIdAndStudentLogin(textExercise.getId(), "student2").get();
        assertThat(storedParticipation.getSubmissions().size()).as("submission was initialized").isEqualTo(1);
        assertThat(storedParticipation.getSubmissions().iterator().next().getClass()).as("submission is of type text submission").isEqualTo(TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateTwiceInModelingExercise_badRequest() throws Exception {
        request.post("/api/courses/" + course.getId() + "/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);
        request.post("/api/courses/" + course.getId() + "/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1")
    public void participateTwiceInTextExercise_badRequest() throws Exception {
        request.post("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
        request.post("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1")
    public void getLatestPendingSubmissionIfExists_student() throws Exception {
        ModelingSubmission submission = (ModelingSubmission) new ModelingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(10L));
        submission = database.addModelingSubmission(modelingExercise, submission, "student1");
        request.get("/api/participations/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, Submission.class);
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void getLatestPendingSubmissionIfExists_ta() throws Exception {
        ModelingSubmission submission = (ModelingSubmission) new ModelingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(10L));
        submission = database.addModelingSubmission(modelingExercise, submission, "student1");
        request.get("/api/participations/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, Submission.class);
    }

    @Test
    @WithMockUser(username = "instructor1")
    public void getLatestPendingSubmissionIfExists_instructor() throws Exception {
        ModelingSubmission submission = (ModelingSubmission) new ModelingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(10L));
        submission = database.addModelingSubmission(modelingExercise, submission, "student1");
        request.get("/api/participations/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, Submission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    public void getLatestPendingSubmissionIfNotExists_student() throws Exception {
        // Submission is older than 1 minute, therefore not considered pending.
        ModelingSubmission submission = (ModelingSubmission) new ModelingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addModelingSubmission(modelingExercise, submission, "student1");
        Submission returnedSubmission = request.getNullable("/api/participations/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                Submission.class);
        assertThat(returnedSubmission).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void getLatestPendingSubmissionIfNotExists_ta() throws Exception {
        // Submission is older than 1 minute, therefore not considered pending.
        ModelingSubmission submission = (ModelingSubmission) new ModelingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addModelingSubmission(modelingExercise, submission, "student1");
        Submission returnedSubmission = request.getNullable("/api/participations/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                Submission.class);
        assertThat(returnedSubmission).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1")
    public void getLatestPendingSubmissionIfNotExists_instructor() throws Exception {
        // Submission is older than 1 minute, therefore not considered pending.
        ModelingSubmission submission = (ModelingSubmission) new ModelingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = database.addModelingSubmission(modelingExercise, submission, "student1");
        Submission returnedSubmission = request.getNullable("/api/participations/" + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                Submission.class);
        assertThat(returnedSubmission).isNull();
    }
}
