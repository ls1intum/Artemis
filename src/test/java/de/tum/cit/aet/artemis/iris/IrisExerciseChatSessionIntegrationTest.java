package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.session.IrisExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisStatusDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class IrisExerciseChatSessionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irischatsessionintegration";

    @Autowired
    private IrisExerciseChatSessionRepository irisExerciseChatSessionRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession() throws Exception {
        var irisSession = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisExerciseChatSession.class, HttpStatus.CREATED);
        var actualIrisSession = irisExerciseChatSessionRepository.findByIdElseThrow(irisSession.getId());
        assertThat(actualIrisSession.getUser()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        assertThat(exercise).isEqualTo(actualIrisSession.getExercise());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession_alreadyExists() throws Exception {
        var firstResponse = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var secondResponse = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        assertThat(firstResponse).isNotEqualTo(secondResponse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCurrentSession() throws Exception {
        var irisSession = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var currentIrisSession = request.postWithResponseBody(exerciseChatUrl(exercise.getId()) + "/current", null, IrisSession.class, HttpStatus.OK);
        assertThat(currentIrisSession).isEqualTo(irisSession);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllSessions() throws Exception {
        var irisSession1 = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var irisSession2 = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        List<IrisSession> irisSessions = request.getList(exerciseChatUrl(exercise.getId()), HttpStatus.OK, IrisSession.class);
        assertThat(irisSessions).hasSize(2).containsAll(List.of(irisSession1, irisSession2));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "ADMIN")
    void irisStatus() throws Exception {
        irisRequestMockProvider.mockStatusResponses();
        assertThat(request.get("/api/iris/status", HttpStatus.OK, IrisStatusDTO.class).active()).isTrue();

        // Pyris now became unavailable (mockStatusResponses mocks a failure for the second call)

        // Should still return true, as the status is cached
        assertThat(request.get("/api/iris/status", HttpStatus.OK, IrisStatusDTO.class).active()).isTrue();

        // Wait the TTL time for the cache to expire
        // In tests, this is 500ms
        Thread.sleep(510);

        // Should now return false
        assertThat(request.get("/api/iris/status", HttpStatus.OK, IrisStatusDTO.class).active()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExerciseWithIrisSession() throws Exception {
        var irisSession = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        assertThat(irisExerciseChatSessionRepository.findByIdElseThrow(irisSession.getId())).isNotNull();
        // Set the URL request parameters to prevent an internal server error which is irrelevant for this test
        var url = "/api/programming-exercises/" + exercise.getId() + "?deleteStudentReposBuildPlans=false&deleteBaseReposBuildPlans=false";
        request.delete(url, HttpStatus.OK);
        assertThat(irisExerciseChatSessionRepository.findById(irisSession.getId())).isEmpty();
    }

    private static String exerciseChatUrl(long sessionId) {
        return "/api/iris/exercise-chat/" + sessionId + "/sessions";
    }
}
