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
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisCodeEditorSessionRepository;

class IrisCodeEditorSessionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriscodeeditorsessionintegration";

    @Autowired
    private IrisCodeEditorSessionRepository irisCodeEditorSessionRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 1, 0);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void createSession() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisCodeEditorSession.class,
                HttpStatus.CREATED);
        var actualIrisSession = irisCodeEditorSessionRepository.findByIdElseThrow(irisSession.getId());
        assertThat(actualIrisSession.getUser()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        assertThat(exercise).isEqualTo(actualIrisSession.getExercise());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void createSession_alreadyExists() throws Exception {
        var firstResponse = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisSession.class,
                HttpStatus.CREATED);
        var secondResponse = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisSession.class,
                HttpStatus.CREATED);
        assertThat(firstResponse).isNotEqualTo(secondResponse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getCurrentSession() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisSession.class,
                HttpStatus.CREATED);
        var currentIrisSession = request.get("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions/current", HttpStatus.OK, IrisSession.class);
        assertThat(currentIrisSession).isEqualTo(irisSession);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getCurrentSession_notFound() throws Exception {
        request.get("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions/current", HttpStatus.NOT_FOUND, IrisSession.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getAllSessions() throws Exception {
        var irisSession1 = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisSession.class,
                HttpStatus.CREATED);
        var irisSession2 = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisSession.class,
                HttpStatus.CREATED);
        List<IrisSession> irisSessions = request.getList("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", HttpStatus.OK, IrisSession.class);
        assertThat(irisSessions).hasSize(2).containsAll(List.of(irisSession1, irisSession2));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "ADMIN")
    void isActive() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisSession.class,
                HttpStatus.CREATED);
        var settings = irisSettingsService.getGlobalSettings();

        irisRequestMockProvider.mockStatusResponse();
        irisRequestMockProvider.mockStatusResponse();
        irisRequestMockProvider.mockStatusResponse();

        // FIXME: This test fails because preferredModel is null when the REST call is made despite it being set here
        settings.getIrisCodeEditorSettings().setPreferredModel("TEST_MODEL_UP");
        irisSettingsService.saveIrisSettings(settings);
        assertThat(request.get("/api/iris/code-editor-sessions/" + irisSession.getId() + "/active", HttpStatus.OK, Boolean.class)).isTrue();
    }
}
