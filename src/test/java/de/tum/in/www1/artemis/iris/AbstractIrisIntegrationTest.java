package de.tum.in.www1.artemis.iris;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Objects;
import java.util.stream.Collectors;

import javax.ws.rs.BadRequestException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.IrisRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.repository.iris.IrisTemplateRepository;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;
import de.tum.in.www1.artemis.user.UserUtilService;

public abstract class AbstractIrisIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    protected CourseRepository courseRepository;

    @Autowired
    protected IrisSettingsService irisSettingsService;

    @Autowired
    protected IrisTemplateRepository irisTemplateRepository;

    @Autowired
    @Qualifier("irisRequestMockProvider")
    protected IrisRequestMockProvider irisRequestMockProvider;

    @Autowired
    protected ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    protected UserUtilService userUtilService;

    @Autowired
    protected ExerciseUtilService exerciseUtilService;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected IrisSessionRepository irisSessionRepository;

    private static final long TIMEOUT_MS = 200;

    private final String chatUri = "/topic/iris/sessions/";

    private final String codeEditorUri = "/topic/iris/code-editor-sessions/";

    private String uri;

    @BeforeEach
    void setup() {
        irisRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        irisRequestMockProvider.reset();
    }

    protected void activateIrisGlobally() {
        var globalSettings = irisSettingsService.getGlobalSettings();
        globalSettings.getIrisChatSettings().setEnabled(true);
        globalSettings.getIrisChatSettings().setPreferredModel(null);
        globalSettings.getIrisHestiaSettings().setEnabled(true);
        globalSettings.getIrisHestiaSettings().setPreferredModel(null);
        irisSettingsService.saveGlobalIrisSettings(globalSettings);
    }

    protected void activateIrisFor(Course course) {
        var courseWithSettings = irisSettingsService.addDefaultIrisSettingsTo(course);
        courseWithSettings.getIrisSettings().getIrisChatSettings().setEnabled(true);
        courseWithSettings.getIrisSettings().getIrisChatSettings().setTemplate(createDummyTemplate());
        courseWithSettings.getIrisSettings().getIrisChatSettings().setPreferredModel(null);
        courseWithSettings.getIrisSettings().getIrisHestiaSettings().setEnabled(true);
        courseWithSettings.getIrisSettings().getIrisHestiaSettings().setTemplate(createDummyTemplate());
        courseWithSettings.getIrisSettings().getIrisHestiaSettings().setPreferredModel(null);
        courseRepository.save(courseWithSettings);
    }

    protected void activateIrisFor(ProgrammingExercise exercise) {
        var exerciseWithSettings = irisSettingsService.addDefaultIrisSettingsTo(exercise);
        exerciseWithSettings.getIrisSettings().getIrisChatSettings().setEnabled(true);
        exerciseWithSettings.getIrisSettings().getIrisChatSettings().setTemplate(createDummyTemplate());
        exerciseWithSettings.getIrisSettings().getIrisChatSettings().setPreferredModel(null);
        programmingExerciseRepository.save(exerciseWithSettings);
    }

    protected IrisTemplate createDummyTemplate() {
        var template = new IrisTemplate();
        template.setContent("Hello World");
        return template;
    }

    /**
     * Verify that the message was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     * @param message   the content of the message
     */
    protected void verifyMessageWasSentOverWebsocket(String user, Long sessionId, String message) {
        getIrisSessionType(sessionId);
        verify(websocketMessagingService, timeout(TIMEOUT_MS).times(1)).sendMessageToUser(eq(user), eq(uri + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisChatWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisChatWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.MESSAGE
                        && Objects.equals(websocketDTO.getMessage().getContent().stream().map(IrisMessageContent::getContentAsString).collect(Collectors.joining("\n")), message)));
    }

    /**
     * Verify that the message was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     * @param message   the message
     */
    protected void verifyMessageWasSentOverWebsocket(String user, Long sessionId, IrisMessage message) {
        getIrisSessionType(sessionId);
        verify(websocketMessagingService, timeout(TIMEOUT_MS).times(1)).sendMessageToUser(eq(user), eq(uri + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisChatWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisChatWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.MESSAGE
                        && Objects.equals(websocketDTO.getMessage().getContent().stream().map(IrisMessageContent::getContentAsString).toList(),
                                message.getContent().stream().map(IrisMessageContent::getContentAsString).toList())));
    }

    /**
     * Verify that an error was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyErrorWasSentOverWebsocket(String user, Long sessionId) {
        getIrisSessionType(sessionId);
        verify(websocketMessagingService, timeout(TIMEOUT_MS).times(1)).sendMessageToUser(eq(user), eq(uri + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisChatWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisChatWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.ERROR));
    }

    /**
     * Verify that nothing was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyNothingWasSentOverWebsocket(String user, Long sessionId) {
        getIrisSessionType(sessionId);
        verify(websocketMessagingService, times(0)).sendMessageToUser(eq(user), eq(uri + sessionId), any());
    }

    /**
     * Verify that nothing else was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyNothingElseWasSentOverWebsocket(String user, Long sessionId) {
        verifyNoMoreInteractions(websocketMessagingService);
    }

    /**
     * Verify that no error was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyNoErrorWasSentOverWebsocket(String user, Long sessionId) {
        getIrisSessionType(sessionId);
        verify(websocketMessagingService, times(0)).sendMessageToUser(eq(user), eq(uri + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisChatWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisChatWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.ERROR));
    }

    private void getIrisSessionType(Long sessionId) {
        var session = irisSessionRepository.findByIdElseThrow(sessionId);
        if (session instanceof IrisChatSession) {
            uri = chatUri;

            return;
        }
        if (session instanceof IrisCodeEditorSession) {
            uri = codeEditorUri;
            return;
        }
        throw new BadRequestException("Invalid Iris session type for websocket " + session.getClass().getSimpleName());
    }
}
