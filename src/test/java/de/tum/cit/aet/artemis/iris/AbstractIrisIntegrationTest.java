package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.userTopic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisCallbackTokenService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
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

    @Autowired
    protected PyrisCallbackTokenService pyrisCallbackTokenService;

    @Autowired
    private PyrisJobService callbackJobService;

    private static final long TIMEOUT_MS = 200;

    /**
     * What a Pyris callback carries: the run id in the URL and the token as Bearer credential.
     *
     * @param runId the run id
     * @param token the callback token of the run
     */
    protected record PyrisCallbackCredentials(String runId, String token) {

        /**
         * @return headers carrying the token as Bearer credential
         */
        public HttpHeaders headers() {
            var headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, Constants.BEARER_PREFIX + token);
            return headers;
        }
    }

    /**
     * The credentials Pyris would use for a run, from either what Artemis sent to Pyris (the callback token in the
     * execution settings) or the run id of a job a test registered directly, for which a token is issued here.
     *
     * @param runIdOrToken a callback token, or the run id of a registered job
     * @return the run id and token to call back with
     */
    protected PyrisCallbackCredentials pyrisCallbackCredentials(String runIdOrToken) {
        return pyrisCallbackTokenService.verify(runIdOrToken).map(verified -> new PyrisCallbackCredentials(verified.runId(), runIdOrToken))
                .orElseGet(() -> new PyrisCallbackCredentials(runIdOrToken, pyrisCallbackTokenService.issue(callbackJobService.getJob(runIdOrToken))));
    }

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
     * Decides the legacy build-trigger switch for a course, i.e. whether Artemis' own
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
     * Assert that a value never left the server over the websocket: neither in the serialized form the client receives nor in the
     * string form of any argument, for every message sent since the last reset.
     *
     * @param secret the value that must not appear
     */
    protected void assertNoWebsocketPayloadContains(String secret) {
        var payloads = Mockito.mockingDetails(websocketMessagingService).getInvocations().stream().flatMap(invocation -> Arrays.stream(invocation.getArguments())).map(argument -> {
            try {
                return JsonObjectMapper.get().writeValueAsString(argument) + " " + argument;
            }
            catch (RuntimeException e) {
                return String.valueOf(argument);
            }
        }).toList();
        assertThat(payloads).as("messages were sent over the websocket").isNotEmpty();
        assertThat(payloads).as("no websocket payload may contain the value").noneMatch(payload -> payload.contains(secret));
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
                userTopic("/topic/iris/" + topicSuffix),
                ArgumentMatchers.argThat(matcher));
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
                userTopic("/topic/iris/" + topicSuffix),
                any());
        // @formatter:on
    }
}
