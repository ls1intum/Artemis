package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LearningGoalRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class LearningGoalResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    LearningGoalRepository learningGoalRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    RequestUtilService requestUtilService;

    private Course course1;

    private LearningGoal learningGoal;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 1, 1);
        course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "instructor");
        courseRepository.save(course1);

        learningGoal = new LearningGoal();
        learningGoal.setTitle("Example Learning Goal");
        learningGoal.setDescription("Example Description");
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
        learningGoal = null;
        learningGoalRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateLearningGoal_validRequest_CREATED() throws Exception {
        learningGoal.setCourse(course1);
        LearningGoal response = requestUtilService.postWithResponseBody("/api/goals", learningGoal, LearningGoal.class, HttpStatus.CREATED);
        assertThat(response.getTitle()).as("title is the same").isEqualTo(learningGoal.getTitle());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateLearningGoal_idAlreadySet_BAD_REQUEST() throws Exception {
        learningGoal.setCourse(course1);
        learningGoal.setId(1L);
        requestUtilService.post("/api/goals", learningGoal, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateLearningGoal_courseNull_CONFLICT() throws Exception {
        requestUtilService.post("/api/goals", learningGoal, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateLearningGoal_student_FORBIDDEN() throws Exception {
        learningGoal.setCourse(course1);
        requestUtilService.post("/api/goals", learningGoal, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCreateLearningGoal_tutor_FORBIDDEN() throws Exception {
        learningGoal.setCourse(course1);
        requestUtilService.post("/api/goals", learningGoal, HttpStatus.FORBIDDEN);
    }

}
