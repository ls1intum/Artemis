package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.session.IrisExerciseCreationSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisExerciseCreationSessionRepository;

class IrisExerciseCreationSessionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irisexcreationsessionintegration";

    @Autowired
    private IrisExerciseCreationSessionRepository irisExerciseCreationSessionRepository;

    private ProgrammingExercise exercise;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 1, 0);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void createSession() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/courses/" + course.getId() + "/exercise-creation-sessions", null, IrisExerciseCreationSession.class,
                HttpStatus.CREATED);
        var actualIrisSession = irisExerciseCreationSessionRepository.findByIdElseThrow(irisSession.getId());
        assertThat(actualIrisSession.getUser()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        assertThat(course).isEqualTo(actualIrisSession.getCourse());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void createSession_alreadyExists() throws Exception {
        var firstResponse = request.postWithResponseBody("/api/iris/courses/" + course.getId() + "/exercise-creation-sessions", null, IrisSession.class, HttpStatus.CREATED);
        var secondResponse = request.postWithResponseBody("/api/iris/courses/" + course.getId() + "/exercise-creation-sessions", null, IrisSession.class, HttpStatus.CREATED);
        assertThat(firstResponse).isNotEqualTo(secondResponse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getCurrentSession() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/courses/" + course.getId() + "/exercise-creation-sessions", null, IrisSession.class, HttpStatus.CREATED);
        var currentIrisSession = request.get("/api/iris/courses/" + course.getId() + "/exercise-creation-sessions/current", HttpStatus.OK, IrisSession.class);
        assertThat(currentIrisSession).isEqualTo(irisSession);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getCurrentSession_notFound() throws Exception {
        request.get("/api/iris/courses/" + course.getId() + "/exercise-creation-sessions/current", HttpStatus.NOT_FOUND, IrisSession.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getAllSessions() throws Exception {
        var irisSession1 = request.postWithResponseBody("/api/iris/courses/" + course.getId() + "/exercise-creation-sessions", null, IrisSession.class, HttpStatus.CREATED);
        var irisSession2 = request.postWithResponseBody("/api/iris/courses/" + course.getId() + "/exercise-creation-sessions", null, IrisSession.class, HttpStatus.CREATED);
        List<IrisSession> irisSessions = request.getList("/api/iris/courses/" + course.getId() + "/exercise-creation-sessions", HttpStatus.OK, IrisSession.class);
        assertThat(irisSessions).hasSize(2).containsAll(List.of(irisSession1, irisSession2));
    }
}
