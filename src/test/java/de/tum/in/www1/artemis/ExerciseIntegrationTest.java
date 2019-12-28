package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.*;
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
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.StatsForInstructorDashboardDTO;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ExerciseIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @BeforeEach
    public void init() {
        database.addUsers(10, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetStatsForTutorExerciseDashboardTest() throws Exception {
        List<Course> courses = database.createCoursesWithExercises();
        Course course = courses.get(0);
        TextExercise textExercise = (TextExercise) course.getExercises().stream().filter(e -> e instanceof TextExercise).findFirst().get();
        List<Submission> submissions = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.text("Text");
            textSubmission.submitted(true);
            textSubmission.submissionDate(ZonedDateTime.now());
            submissions.add(database.addSubmission(textExercise, textSubmission, "student" + (i + 1))); // student1 was already used
            if (i % 3 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"));
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.SEMI_AUTOMATIC, database.getUserByLogin("instructor1"));
            }
        }
        StatsForInstructorDashboardDTO statsForInstructorDashboardDTO = request.get("/api/exercises/" + textExercise.getId() + "/stats-for-tutor-dashboard", HttpStatus.OK,
                StatsForInstructorDashboardDTO.class);
        assertThat(statsForInstructorDashboardDTO.getNumberOfSubmissions()).isEqualTo(submissions.size() + 1);
        assertThat(statsForInstructorDashboardDTO.getNumberOfAssessments()).isEqualTo(3);
        assertThat(statsForInstructorDashboardDTO.getNumberOfAutomaticAssistedAssessments()).isEqualTo(1);

        for (Exercise exercise : course.getExercises()) {
            StatsForInstructorDashboardDTO stats = request.get("/api/exercises/" + exercise.getId() + "/stats-for-tutor-dashboard", HttpStatus.OK,
                    StatsForInstructorDashboardDTO.class);
            assertThat(stats.getNumberOfComplaints()).isEqualTo(0);
            assertThat(stats.getNumberOfMoreFeedbackRequests()).isEqualTo(0);
        }
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercises();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseServer = request.get("/api/exercises/" + exercise.getId(), HttpStatus.OK, Exercise.class);
                // TODO: check some entries and check that some are filtered out
            }
        }
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetExerciseDetails() throws Exception {
        List<Course> courses = database.createCoursesWithExercises();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseWithDetails = request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.OK, Exercise.class);
                // TODO: check some entries (e.g. that this includes student questions, results, etc. and check that some are filtered out
            }
        }
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetExerciseForTutorDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercises();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseForTutorDashboard = request.get("/api/exercises/" + exercise.getId() + "/for-tutor-dashboard", HttpStatus.OK, Exercise.class);
                // TODO: check some entries (e.g. that this includes student questions, results, etc. and check that some are filtered out
            }
        }
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetStatsForTutorExerciseDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercises();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                StatsForInstructorDashboardDTO exerciseServer = request.get("/api/exercises/" + exercise.getId() + "/stats-for-tutor-dashboard", HttpStatus.OK,
                        StatsForInstructorDashboardDTO.class);
                // TODO: check some entries (e.g. that this includes student questions, results, etc. and check that some are filtered out
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetStatsForInstructorExerciseDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercises();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                StatsForInstructorDashboardDTO exerciseServer = request.get("/api/exercises/" + exercise.getId() + "/stats-for-instructor-dashboard", HttpStatus.OK,
                        StatsForInstructorDashboardDTO.class);
                // TODO: check some entries (e.g. that this includes student questions, results, etc. and check that some are filtered out
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercises();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                request.delete("/api/exercises/" + exercise.getId(), HttpStatus.OK);
            }
        }

        assertThat(exerciseRepository.findAll()).hasSize(0);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testResetExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercises();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                request.delete("/api/exercises/" + exercise.getId() + "/reset", HttpStatus.OK);
                // TODO: check that all participations are deleted for the exercise
                // TODO: also test this for quiz exercises
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCleanupExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercises();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                request.delete("/api/exercises/" + exercise.getId() + "/cleanup", HttpStatus.OK);
                // TODO: check all participations in programming exercises do not have build plan ids any more
            }
        }
        // NOTE: for some reason, the cleanup does not work properly in this case.
        // Therefore we have some additional cleanup code here

        resultRepository.deleteAll();
        submissionRepository.deleteAll();
        exerciseRepository.deleteAll();
    }
}
