package de.tum.cit.aet.artemis.iris;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;
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
        // Global toggles have been removed; keeping this hook for backwards-compatible test setups.
    }

    protected void disableIrisGlobally() {
        // Global toggles have been removed; keeping this hook for backwards-compatible test setups.
    }

    /**
     * Enables Iris for the provided course using the new course-level settings payload.
     *
     * @param course the course that should have Iris enabled
     */
    protected void enableIrisFor(Course course) {
        var current = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(), IrisCourseSettings.of(true, current.customInstructions(), current.variant(), current.supportLevel(),
                current.rateLimit(), current.proactiveStruggleEnabled(), current.legacyBuildTriggersEnabled()), true);
    }

    /**
     * Disables Iris for the provided course.
     *
     * @param course the course to disable Iris for
     */
    protected void disableIrisFor(Course course) {
        var current = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(), IrisCourseSettings.of(false, current.customInstructions(), current.variant(), current.supportLevel(),
                current.rateLimit(), current.proactiveStruggleEnabled(), current.legacyBuildTriggersEnabled()), true);
    }

    /**
     * Turns proactive struggle detection on or off for the course, carrying every other setting through unchanged.
     * {@code activateIrisFor} leaves it off, which is the production default, so a test that exercises the feature
     * has to switch it on explicitly.
     *
     * @param course  the course whose settings to change
     * @param enabled whether proactive struggle detection should be on
     */
    protected void setProactiveStruggleFor(Course course, boolean enabled) {
        var current = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(), IrisCourseSettings.of(current.enabled(), current.customInstructions(), current.variant(), current.supportLevel(),
                current.rateLimit(), enabled, current.legacyBuildTriggersEnabled()), true);
    }

    /**
     * Decides the admin-only legacy build-trigger switch for a course, i.e. whether Artemis' own
     * {@code build_failed} / {@code progress_stalled} events may fire for it.
     *
     * @param course  the course to update
     * @param enabled the explicit decision (the field's third state, "undecided", is what an untouched course has)
     */
    protected void setLegacyBuildTriggersFor(Course course, boolean enabled) {
        var current = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(), IrisCourseSettings.of(current.enabled(), current.customInstructions(), current.variant(), current.supportLevel(),
                current.rateLimit(), current.proactiveStruggleEnabled(), enabled), true);
    }

    /**
     * Sets course level custom instructions and variant for the provided course.
     *
     * @param course             the target course
     * @param customInstructions instructions to be stored (nullable)
     * @param variant            pipeline variant to apply
     */
    protected void configureCourseSettings(Course course, String customInstructions, IrisPipelineVariant variant) {
        var current = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(), IrisCourseSettings.of(current.enabled(), customInstructions, variant, current.supportLevel(), current.rateLimit(),
                current.proactiveStruggleEnabled(), current.legacyBuildTriggersEnabled()), true);
    }

    /**
     * Applies a rate-limit override for the given course.
     *
     * @param course   the course to update
     * @param requests number of requests allowed (null for default/unlimited)
     * @param hours    timeframe in hours (null for default/unlimited)
     */
    protected void configureCourseRateLimit(Course course, Integer requests, Integer hours) {
        var current = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(), IrisCourseSettings.of(current.enabled(), current.customInstructions(), current.variant(), current.supportLevel(),
                new IrisRateLimitConfiguration(requests, hours), current.proactiveStruggleEnabled(), current.legacyBuildTriggersEnabled()), true);
    }

    protected void activateIrisFor(Course course) {
        enableIrisFor(course);
    }

    protected void activateIrisFor(Exercise exercise) {
        enableIrisFor(exercise.getCourseViaExerciseGroupOrCourseMember());
    }

    protected void disableIrisFor(Exercise exercise) {
        disableIrisFor(exercise.getCourseViaExerciseGroupOrCourseMember());
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
