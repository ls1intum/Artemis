package de.tum.in.www1.artemis.iris;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.IrisRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.repository.iris.IrisTemplateRepository;
import de.tum.in.www1.artemis.service.iris.IrisWebsocketService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
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
    private IrisSettingsRepository irisSettingsRepository;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    private static final long TIMEOUT_MS = 200;

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
        irisSettingsRepository.save(globalSettings);
    }

    protected void activateIrisFor(Course course) {
        var courseSettings = irisSettingsService.getDefaultSettingsFor(course);
        courseSettings.getIrisChatSettings().setEnabled(true);
        courseSettings.getIrisChatSettings().setTemplate(createDummyTemplate());
        courseSettings.getIrisChatSettings().setPreferredModel(null);
        courseSettings.getIrisHestiaSettings().setEnabled(true);
        courseSettings.getIrisHestiaSettings().setTemplate(createDummyTemplate());
        courseSettings.getIrisHestiaSettings().setPreferredModel(null);
        courseSettings.getIrisCodeEditorSettings().setEnabled(true);
        courseSettings.getIrisCodeEditorSettings().setChatTemplate(createDummyTemplate());
        courseSettings.getIrisCodeEditorSettings().setProblemStatementGenerationTemplate(createDummyTemplate());
        courseSettings.getIrisCodeEditorSettings().setTemplateRepoGenerationTemplate(null);
        courseSettings.getIrisCodeEditorSettings().setSolutionRepoGenerationTemplate(null);
        courseSettings.getIrisCodeEditorSettings().setTestRepoGenerationTemplate(null);
        courseSettings.getIrisCodeEditorSettings().setPreferredModel(null);
        irisSettingsRepository.save(courseSettings);
    }

    protected void activateIrisFor(ProgrammingExercise exercise) {
        var exerciseSettings = irisSettingsService.getDefaultSettingsFor(exercise);
        exerciseSettings.getIrisChatSettings().setEnabled(true);
        exerciseSettings.getIrisChatSettings().setTemplate(createDummyTemplate());
        exerciseSettings.getIrisChatSettings().setPreferredModel(null);
        irisSettingsRepository.save(exerciseSettings);
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
        verify(websocketMessagingService, timeout(TIMEOUT_MS).times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.MESSAGE
                        && Objects.equals(websocketDTO.getMessage().getContent().stream().map(IrisMessageContent::getTextContent).collect(Collectors.joining("\n")), message)));
    }

    /**
     * Verify that the message was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     * @param message   the message
     */
    protected void verifyMessageWasSentOverWebsocket(String user, Long sessionId, IrisMessage message) {
        verify(websocketMessagingService, timeout(TIMEOUT_MS).times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.MESSAGE
                        && Objects.equals(websocketDTO.getMessage().getContent().stream().map(IrisMessageContent::getTextContent).toList(),
                                message.getContent().stream().map(IrisMessageContent::getTextContent).toList())));
    }

    /**
     * Verify that an error was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyErrorWasSentOverWebsocket(String user, Long sessionId) {
        verify(websocketMessagingService, timeout(TIMEOUT_MS).times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.ERROR));
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
}
