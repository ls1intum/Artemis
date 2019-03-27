package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class ParticipationIntegrationTest {
    @Autowired
    CourseRepository courseRepo;
    @Autowired
    ExerciseRepository exerciseRepo;
    @Autowired
    UserRepository userRepo;
    @Autowired
    RequestUtilService request;
    @Autowired
    DatabaseUtilService database;


    @Before
    public void initTestCase() {
        database.resetDatabase();
        database.addUsers(2, 0);
        database.addCourseWithDifferentModelingExercises();
    }


    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void participateInExercise() throws Exception {
        Course course = courseRepo.findAll().get(0);
        Exercise exercise = exerciseRepo.findAll().get(0);
        URI location =
            request.post(
                "/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/participations",
                null,
                HttpStatus.CREATED);
        Participation participation =
            request.get(location.getPath(), HttpStatus.OK, Participation.class);
        assertThat(participation.getExercise())
            .as("participated in correct exercise")
            .isEqualTo(exercise);
        assertThat(participation.getSubmissions()).as("no submissions on initialization").isEmpty();
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getStudent().getLogin())
            .as("Correct student got set")
            .isEqualTo("student1");
    }
}
