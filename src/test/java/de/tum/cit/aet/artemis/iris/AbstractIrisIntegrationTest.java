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

import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisBuildFailedEventSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisJolEventSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisProgressStalledEventSettings;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

public abstract class AbstractIrisIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    protected IrisSettingsService irisSettingsService;

    @Autowired
    @Qualifier("irisRequestMockProvider")
    protected IrisRequestMockProvider irisRequestMockProvider;

    @Autowired
    protected ProgrammingExerciseTestRepository programmingExerciseRepository;

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
        activateSubSettings(globalSettings.getIrisLectureIngestionSettings());
        activateSubSettings(globalSettings.getIrisCompetencyGenerationSettings());
        activateSubSettings(globalSettings.getIrisTextExerciseChatSettings());
        activateSubSettings(globalSettings.getIrisProactivitySettings());

        // Active Iris events
        var eventSettings = globalSettings.getIrisProactivitySettings().getEventSettings();

        activateEventSettingsFor(IrisProgressStalledEventSettings.class, eventSettings);
        activateEventSettingsFor(IrisBuildFailedEventSettings.class, eventSettings);
        activateEventSettingsFor(IrisJolEventSettings.class, eventSettings);

        irisSettingsRepository.save(globalSettings);
    }

    /**
     * Sets a type of IrisSubSettings to enabled and their preferred model to null.
     *
     * @param settings the settings to be enabled
     */
    private void activateSubSettings(IrisSubSettings settings) {
        settings.setEnabled(true);
        settings.setSelectedVariant("default");
        settings.setAllowedVariants(new TreeSet<>(Set.of("default")));
    }

    /**
     * Activates the given event settings for the given type of event.
     *
     * @param eventSettingsClass the type of event settings
     * @param settings           the settings to be activated
     */
    private <S extends IrisEventSettings> void activateEventSettingsFor(Class<S> eventSettingsClass, Set<IrisEventSettings> settings) {
        settings.stream().filter(e -> e != null && e.getClass() == eventSettingsClass).forEach(e -> {
            e.setActive(true);
        });
    }

    /**
     * Deactivates the given event settings for the given type of event on the course.
     *
     * @param eventSettingsClass the type of event settings
     * @param course             the course for which the settings should be deactivated
     */
    public <S extends IrisEventSettings> void deactivateEventSettingsFor(Class<S> eventSettingsClass, Course course) {
        var courseSettings = irisSettingsService.getRawIrisSettingsFor(course);
        var eventSettings = courseSettings.getIrisProactivitySettings().getEventSettings();

        deactivateEventSettingsFor(eventSettingsClass, eventSettings);
        irisSettingsRepository.save(courseSettings);
    }

    /**
     * Deactivates the given event settings for the given type of event.
     *
     * @param eventSettingsClass the type of event settings
     * @param settings           the settings to be deactivated
     */
    private <S extends IrisEventSettings> void deactivateEventSettingsFor(Class<S> eventSettingsClass, Set<IrisEventSettings> settings) {
        settings.stream().filter(e -> e != null && e.getClass() == eventSettingsClass).forEach(e -> e.setActive(false));
    }

    protected void activateIrisFor(Course course) {
        var courseSettings = irisSettingsService.getDefaultSettingsFor(course);

        activateSubSettings(courseSettings.getIrisChatSettings());

        activateSubSettings(courseSettings.getIrisCompetencyGenerationSettings());

        activateSubSettings(courseSettings.getIrisLectureIngestionSettings());

        activateSubSettings(courseSettings.getIrisTextExerciseChatSettings());

        activateSubSettings(courseSettings.getIrisProactivitySettings());

        // Active Iris events
        var eventSettings = courseSettings.getIrisProactivitySettings().getEventSettings();

        activateEventSettingsFor(IrisProgressStalledEventSettings.class, eventSettings);
        activateEventSettingsFor(IrisBuildFailedEventSettings.class, eventSettings);
        activateEventSettingsFor(IrisJolEventSettings.class, eventSettings);

        irisSettingsRepository.save(courseSettings);
    }

    protected void activateIrisFor(Exercise exercise) {
        var exerciseSettings = irisSettingsService.getDefaultSettingsFor(exercise);
        activateSubSettings(exerciseSettings.getIrisChatSettings());
        activateSubSettings(exerciseSettings.getIrisTextExerciseChatSettings());
        irisSettingsRepository.save(exerciseSettings);
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
