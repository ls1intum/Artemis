package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;

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
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class CourseIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    ParticipationRepository participationRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    SubmissionRepository submissionRepo;

    @Autowired
    UserRepository userRepo;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 0, 0);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void createCourseWithPermission() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        request.post("/api/courses", course, HttpStatus.CREATED);
        List<Course> repoContent = courseRepo.findAll();
        assertThat(repoContent.size()).as("Course got stored").isEqualTo(1);

        course = ModelFactory.generateCourse(1L, null, null, new HashSet<>());
        request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
        assertThat(courseRepo.findAll()).as("Course has not been stored").contains(repoContent.toArray(new Course[0]));
    }

    @Test
    @WithMockUser(roles = "USER")
    public void createCourseWithoutPermission() throws Exception {
        Course course = ModelFactory.generateCourse(null, null, null, new HashSet<>());
        request.post("/api/courses", course, HttpStatus.FORBIDDEN);
        assertThat(courseRepo.findAll().size()).as("Course got stored").isEqualTo(0);
    }

    @Test
    @WithMockUser(roles = "USER")
    public void getCourseWithoutPermission() throws Exception {
        request.getList("/api/courses", HttpStatus.FORBIDDEN, Course.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getAllCoursesForDashboard() throws Exception {
        prepareGetAllCoursesForDashboard();
        // Do the actual request that is tested here.
        List<Course> courses = request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);

        // Test that the prepared inactive course was filtered out.
        assertThat(courses.size()).as("Inactive course was filtered out").isEqualTo(1);
        // Test that the remaining course has two exercises.
        assertThat(courses.get(0).getExercises().size()).as("Two exercises are returned").isEqualTo(2);

        // Iterate over all exercises of the remaining course.
        for (Exercise exercise : courses.get(0).getExercises()) {
            // Test that grading instructions were filtered out as the test user is a student.
            assertThat(exercise.getGradingInstructions()).as("Grading instructions are filtered out").isNull();
            // Test that the exercise does not have more than one participation.
            assertThat(exercise.getStudentParticipations().size()).as("At most one participation").isLessThanOrEqualTo(1);
            if (exercise.getStudentParticipations().size() > 0) {
                // Buffer participation so that null checking is easier.
                Participation participation = exercise.getStudentParticipations().iterator().next();
                if (participation.getSubmissions().size() > 0) {
                    // The call filters participations by submissions and their result. After the call each participation shouldn't have more than one submission.
                    assertThat(participation.getSubmissions().size()).as("At most one submission").isLessThanOrEqualTo(1);
                    Submission submission = participation.getSubmissions().iterator().next();
                    if (submission != null) {
                        // Test that the correct text submission was filtered.
                        if (submission instanceof TextSubmission) {
                            TextSubmission textSubmission = (TextSubmission) submission;
                            assertThat(textSubmission.getText()).as("Correct text submission").isEqualTo("text");
                        }

                        // Test that the correct modeling submission was filtered.
                        if (submission instanceof ModelingSubmission) {
                            ModelingSubmission modelingSubmission = (ModelingSubmission) submission;
                            assertThat(modelingSubmission.getModel()).as("Correct modeling submission").isEqualTo("model2");
                        }
                    }
                }
            }
        }
    }

    private void prepareGetAllCoursesForDashboard() {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), pastTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");

        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course1);
        modelingExercise.setGradingInstructions("some grading instructions");
        course1.addExercises(modelingExercise);

        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
        textExercise.setGradingInstructions("some grading instructions");
        course1.addExercises(textExercise);

        courseRepo.save(course1);
        courseRepo.save(course2);

        exerciseRepo.save(modelingExercise);
        textExercise = exerciseRepo.save(textExercise);

        User user = (userRepo.findOneByLogin("student1")).get();
        Participation participation1 = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, user);
        Participation participation2 = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, user);
        Participation participation3 = ModelFactory.generateStudentParticipation(InitializationState.UNINITIALIZED, modelingExercise, user);

        Submission modelingSubmission1 = ModelFactory.generateModelingSubmission("model1", true);
        Submission modelingSubmission2 = ModelFactory.generateModelingSubmission("model2", true);
        Submission textSubmission = ModelFactory.generateTextSubmission("text", Language.ENGLISH, true);

        Result result1 = ModelFactory.generateResult(true, 10);
        Result result2 = ModelFactory.generateResult(true, 12);
        Result result3 = ModelFactory.generateResult(false, 0);

        result1 = resultRepo.save(result1);
        result2 = resultRepo.save(result2);
        result3 = resultRepo.save(result3);

        modelingSubmission1.setResult(result1);
        modelingSubmission2.setResult(result2);
        textSubmission.setResult(result3);

        participation1 = participationRepo.save(participation1);
        participation2 = participationRepo.save(participation2);
        participation3 = participationRepo.save(participation3);

        modelingSubmission1.setParticipation(participation1);
        textSubmission.setParticipation(participation1);
        modelingSubmission2.setParticipation(participation3);

        submissionRepo.save(modelingSubmission1);
        submissionRepo.save(modelingSubmission2);
        submissionRepo.save(textSubmission);
    }

    public void loadInitialCourses() {
        Course course = ModelFactory.generateCourse(1L, null, null, new HashSet<>());
        assertThat(courseRepo.findAll()).as("course repo got initialized correctly").contains(course);
    }
}
