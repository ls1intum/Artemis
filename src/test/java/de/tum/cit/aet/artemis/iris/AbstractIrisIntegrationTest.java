package de.tum.cit.aet.artemis.iris;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.IrisTemplate;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

public abstract class AbstractIrisIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    protected IrisSettingsService irisSettingsService;

    @Autowired
    @Qualifier("irisRequestMockProvider")
    protected IrisRequestMockProvider irisRequestMockProvider;

    @Autowired
    protected ProgrammingExerciseRepository programmingExerciseRepository;

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
        activateSubSettings(globalSettings.getIrisChatSettings());
        activateSubSettings(globalSettings.getIrisHestiaSettings());
        activateSubSettings(globalSettings.getIrisLectureIngestionSettings());
        activateSubSettings(globalSettings.getIrisCompetencyGenerationSettings());
        irisSettingsRepository.save(globalSettings);
    }

    /**
     * Sets a type of IrisSubSettings to enabled and their preferred model to null.
     *
     * @param settings the settings to be enabled
     */
    private void activateSubSettings(IrisSubSettings settings) {
        settings.setEnabled(true);
        settings.setPreferredModel(null);
        settings.setAllowedModels(new TreeSet<>(Set.of("dummy")));
    }

    protected void activateIrisFor(Course course) {
        var courseSettings = irisSettingsService.getDefaultSettingsFor(course);

        activateSubSettings(courseSettings.getIrisChatSettings());
        courseSettings.getIrisChatSettings().setTemplate(createDummyTemplate());

        activateSubSettings(courseSettings.getIrisHestiaSettings());
        courseSettings.getIrisHestiaSettings().setTemplate(createDummyTemplate());

        activateSubSettings(courseSettings.getIrisCompetencyGenerationSettings());
        courseSettings.getIrisCompetencyGenerationSettings().setTemplate(createDummyTemplate());

        activateSubSettings(courseSettings.getIrisLectureIngestionSettings());

        irisSettingsRepository.save(courseSettings);
    }

    protected void activateIrisFor(ProgrammingExercise exercise) {
        var exerciseSettings = irisSettingsService.getDefaultSettingsFor(exercise);
        activateSubSettings(exerciseSettings.getIrisChatSettings());
        exerciseSettings.getIrisChatSettings().setTemplate(createDummyTemplate());
        irisSettingsRepository.save(exerciseSettings);
    }

    protected IrisTemplate createDummyTemplate() {
        return new IrisTemplate("Hello World");
    }

    /**
     * Verify that the given messages were sent through the websocket for the given user and topic.
     *
     * @param userLogin   The user login
     * @param topicSuffix The chat session
     * @param matchers    Argument matchers which describe the messages that should have been sent
     */
    protected void verifyWebsocketActivityWasExactly(String userLogin, String topicSuffix, ArgumentMatcher<?>... matchers) {
        for (ArgumentMatcher<?> callDescriptor : matchers) {
            verifyMessageWasSentOverWebsocket(userLogin, topicSuffix, callDescriptor);
        }
        verifyNumberOfCallsToWebsocket(userLogin, topicSuffix, matchers.length);
    }

    /**
     * Verify that the given message was sent through the websocket for the given user and topic.
     *
     * @param userLogin   The user login
     * @param topicSuffix The topic suffix, e.g. "sessions/123"
     * @param matcher     Argument matcher which describes the message that should have been sent
     */
    protected void verifyMessageWasSentOverWebsocket(String userLogin, String topicSuffix, ArgumentMatcher<?> matcher) {
        // @formatter:off
        verify(websocketMessagingService, timeout(TIMEOUT_MS).times(1))
                .sendMessageToUser(
                        eq(userLogin),
                        eq("/topic/iris/" + topicSuffix),
                        ArgumentMatchers.argThat(matcher)
                );
        // @formatter:on
    }

    /**
     * Verify that exactly `numberOfCalls` messages were sent through the websocket for the given user and topic.
     */
    protected void verifyNumberOfCallsToWebsocket(String userLogin, String topicSuffix, int numberOfCalls) {
        // @formatter:off
        verify(websocketMessagingService, times(numberOfCalls))
                .sendMessageToUser(
                        eq(userLogin),
                        eq("/topic/iris/" + topicSuffix),
                        any()
                );
        // @formatter:on
    }
}
