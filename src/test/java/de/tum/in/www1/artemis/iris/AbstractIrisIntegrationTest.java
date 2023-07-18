package de.tum.in.www1.artemis.iris;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.IrisRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.iris.IrisTemplateRepository;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;
import de.tum.in.www1.artemis.service.iris.IrisWebsocketService;
import de.tum.in.www1.artemis.user.UserUtilService;

@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "bamboo", "bitbucket", "jira", "ldap", "scheduling", "athene", "apollon", "iris" })
public class AbstractIrisIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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

    protected void verifyNoMessageWasSentOverWebsocket() throws InterruptedException {
        Thread.sleep(1000);
        verifyNoInteractions(websocketMessagingService);
    }

    /**
     * Wait for the iris message to be processed by Iris, the LLM mock and the websocket service.
     *
     * @throws InterruptedException if the thread is interrupted
     */
    protected void waitForIrisMessageToBeProcessed() throws InterruptedException {
        Thread.sleep(500);
    }

    /**
     * Verify that the message was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     * @param message   the content of the message
     */
    protected void verifyMessageWasSentOverWebsocket(String user, Long sessionId, String message) {
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.MESSAGE && websocketDTO.getMessage().getContent().size() == 1
                        && Objects.equals(websocketDTO.getMessage().getContent().get(0).getTextContent(), message)));
    }

    /**
     * Verify that the message was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     * @param message   the message
     */
    protected void verifyMessageWasSentOverWebsocket(String user, Long sessionId, IrisMessage message) {
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.MESSAGE && websocketDTO.getMessage().getContent().size() == 1
                        && Objects.equals(websocketDTO.getMessage(), message)));
    }

    /**
     * Verify that an error was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyErrorWasSentOverWebsocket(String user, Long sessionId) {
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.ERROR));
    }

    /**
     * Verify that nothing was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyNothingWasSentOverWebsocket(String user, Long sessionId) {
        verify(websocketMessagingService, times(0)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId), any());
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
     * Verify that an error was sent through the websocket.
     *
     * @param user      the user
     * @param sessionId the session id
     */
    protected void verifyNoErrorWasSentOverWebsocket(String user, Long sessionId) {
        verify(websocketMessagingService, times(0)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId),
                ArgumentMatchers.argThat(object -> object instanceof IrisWebsocketService.IrisWebsocketDTO websocketDTO
                        && websocketDTO.getType() == IrisWebsocketService.IrisWebsocketDTO.IrisWebsocketMessageType.ERROR));
    }
}
