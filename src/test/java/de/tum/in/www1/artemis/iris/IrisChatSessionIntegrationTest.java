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
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisChatSessionRepository;
import de.tum.in.www1.artemis.web.rest.iris.IrisExerciseChatBasedSessionResource.IrisHealthDTO;

class IrisChatSessionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irischatsessionintegration";

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisChatSession.class, HttpStatus.CREATED);
        var actualIrisSession = irisChatSessionRepository.findByIdElseThrow(irisSession.getId());
        assertThat(actualIrisSession.getUser()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        assertThat(exercise).isEqualTo(actualIrisSession.getExercise());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession_alreadyExists() throws Exception {
        var firstResponse = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisSession.class, HttpStatus.CREATED);
        var secondResponse = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisSession.class, HttpStatus.CREATED);
        assertThat(firstResponse).isNotEqualTo(secondResponse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCurrentSession() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisSession.class, HttpStatus.CREATED);
        var currentIrisSession = request.get("/api/iris/programming-exercises/" + exercise.getId() + "/sessions/current", HttpStatus.OK, IrisSession.class);
        assertThat(currentIrisSession).isEqualTo(irisSession);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCurrentSession_notFound() throws Exception {
        request.get("/api/iris/programming-exercises/" + exercise.getId() + "/sessions/current", HttpStatus.NOT_FOUND, IrisSession.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllSessions() throws Exception {
        var irisSession1 = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisSession.class, HttpStatus.CREATED);
        var irisSession2 = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisSession.class, HttpStatus.CREATED);
        List<IrisSession> irisSessions = request.getList("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", HttpStatus.OK, IrisSession.class);
        assertThat(irisSessions).hasSize(2).containsAll(List.of(irisSession1, irisSession2));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "ADMIN")
    void isActive() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, IrisSession.class, HttpStatus.CREATED);
        var settings = irisSettingsService.getGlobalSettings();
        irisRequestMockProvider.mockStatusResponse();
        irisRequestMockProvider.mockStatusResponse();
        irisRequestMockProvider.mockStatusResponse();

        var previousPreferredModel = settings.getIrisChatSettings().getPreferredModel();
        settings.getIrisChatSettings().setPreferredModel("TEST_MODEL_UP");
        irisSettingsService.saveIrisSettings(settings);
        assertThat(request.get("/api/iris/sessions/" + irisSession.getId() + "/active", HttpStatus.OK, IrisHealthDTO.class).active()).isTrue();

        settings.getIrisChatSettings().setPreferredModel(previousPreferredModel);
        irisSettingsService.saveIrisSettings(settings);
    }
}
