package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisChatSessionRepository;

class IrisSessionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irissessionintegration";

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 4, 0, 0, 0);

        final Course course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisChatSession.class, HttpStatus.CREATED);
        var actualIrisSession = irisChatSessionRepository.findByIdElseThrow(irisSession.getId());
        assertThat(actualIrisSession.getUser()).isEqualTo(database.getUserByLogin(TEST_PREFIX + "student1"));
        assertThat(actualIrisSession.getExercise()).isEqualTo(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void createSession_alreadyExists() throws Exception {
        request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisSession.class, HttpStatus.CREATED);
        request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisSession.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void getCurrentSession() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisSession.class, HttpStatus.CREATED);
        var currentIrisSession = request.get("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", HttpStatus.OK, IrisSession.class);
        assertThat(currentIrisSession).isEqualTo(irisSession);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void getCurrentSession_notFound() throws Exception {
        request.get("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", HttpStatus.NOT_FOUND, IrisSession.class);
    }
}
