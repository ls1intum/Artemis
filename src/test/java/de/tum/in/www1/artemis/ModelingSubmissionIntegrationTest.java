package de.tum.in.www1.artemis;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.repository.*;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class ModelingSubmissionIntegrationTest {
    @Autowired
    CourseRepository courseRepo;
    @Autowired
    ExerciseRepository exerciseRepo;
    @Autowired
    RequestUtils request;

    ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);
    ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);
    ZonedDateTime futureFututreTimestamp = ZonedDateTime.now().plusDays(2);


    @Before
    public void resetDatabase() {
        courseRepo.deleteAll();
        exerciseRepo.deleteAll();
        assertThat(courseRepo.findAll()).as("course data has been cleared").isEmpty();
        assertThat(exerciseRepo.findAll()).as("exercise data has been cleared").isEmpty();
    }


    @Test
    @WithMockUser(username = "max", roles = "USER")
    public void initialModelingSubmission() throws Exception {
        initTestCase();
        Course course = courseRepo.findAll().get(0);
        Exercise exercise = exerciseRepo.findAll().get(0);
        Participation participation = participateInExercise(course,exercise);
        ModelingSubmission submission = new ModelingSubmission();


    }


    public void initTestCase() {
        Course course = CourseIntegrationTest.generateCourse(null, pastTimestamp, futureFututreTimestamp, new HashSet<>());
        Exercise exercise = generateExercise(pastTimestamp, futureTimestamp, futureFututreTimestamp, course);
        course.addExercises(exercise);
        courseRepo.save(course);
        exerciseRepo.save(exercise);
        List<Course> courseRepoContent = courseRepo.findAll();
        List<Exercise> exerciseRepoContent = exerciseRepo.findAll();
        assertThat(exerciseRepoContent.size())
            .as("a exercise got stored")
            .isEqualTo(1);
        assertThat(courseRepoContent.size())
            .as("a course got stored")
            .isEqualTo(1);
        assertThat(courseRepoContent.get(0).getExercises().size())
            .as("Course contains exercise")
            .isEqualTo(1);
        assertThat(courseRepoContent.get(0).getExercises().contains(exerciseRepoContent.get(0)))
            .as("course contains the right exercise")
            .isTrue();
    }


    public Participation participateInExercise(Course course, Exercise exercise) throws Exception {
        URI location = request.post("/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/participations", null, HttpStatus.CREATED);
        return request.get(location.getPath(), HttpStatus.OK, Participation.class);
    }


    public ModelingExercise generateExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        return new ModelingExercise(UUID.randomUUID().toString(),
            "t" + UUID.randomUUID().toString().substring(0, 3),
            releaseDate,
            dueDate,
            assessmentDueDate,
            5.0,
            "",
            "",
            new LinkedList<String>(),
            DifficultyLevel.MEDIUM,
            new HashSet<Participation>(),
            new HashSet<TutorParticipation>(),
            course,
            new HashSet<ExampleSubmission>(),
            DiagramType.CLASS,
            "",
            ""
        );
    }
}
