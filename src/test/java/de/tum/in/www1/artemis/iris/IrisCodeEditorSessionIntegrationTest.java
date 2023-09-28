package de.tum.in.www1.artemis.iris;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisCodeEditorSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;

class IrisCodeEditorSessionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriscodeeditorsessionintegration";

    @Autowired
    private IrisCodeEditorSessionRepository irisChatSessionRepository;

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
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisCodeEditorSession.class, HttpStatus.CREATED);
        var actualIrisSession = irisChatSessionRepository.findByIdElseThrow(irisSession.getId());
        assertThat(actualIrisSession.getUser()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        assertThat(exercise).isEqualTo(actualIrisSession.getExercise());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void createSession_alreadyExists() throws Exception {
        var firstResponse = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisSession.class, HttpStatus.CREATED);
        var secondResponse = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisSession.class, HttpStatus.CREATED);
        assertThat(firstResponse).isNotEqualTo(secondResponse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getCurrentSession() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisSession.class, HttpStatus.CREATED);
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
    void isActive() throws Exception {
        var irisSession = request.postWithResponseBody("/api/iris/programming-exercises/" + exercise.getId() + "/code-editor-sessions", null, IrisSession.class, HttpStatus.CREATED);
        var settings = irisSettingsService.getGlobalSettings();
        settings.getIrisChatSettings().setPreferredModel("TEST_MODEL_UP");
        irisSettingsService.saveGlobalIrisSettings(settings);
        irisRequestMockProvider.mockStatusResponse();
        irisRequestMockProvider.mockStatusResponse();
        irisRequestMockProvider.mockStatusResponse();
        assertThat(request.get("/api/iris/code-editor-sessions/" + irisSession.getId() + "/active", HttpStatus.OK, Boolean.class)).isTrue();
        settings.getIrisChatSettings().setPreferredModel("TEST_MODEL_DOWN");
        irisSettingsService.saveGlobalIrisSettings(settings);
        assertThat(request.get("/api/iris/code-editor-sessions/" + irisSession.getId() + "/active", HttpStatus.OK, Boolean.class)).isFalse();
        settings.getIrisChatSettings().setPreferredModel("TEST_MODEL_NA");
        irisSettingsService.saveGlobalIrisSettings(settings);
        assertThat(request.get("/api/iris/code-editor-sessions/" + irisSession.getId() + "/active", HttpStatus.OK, Boolean.class)).isFalse();
    }
}
