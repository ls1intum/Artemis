package de.tum.cit.aet.artemis.programming;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.admin.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.config.RateLimitConfig;
import de.tum.cit.aet.artemis.core.config.RateLimitingProperties;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class ProgrammingExerciseReEvaluationRateLimitIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "reevaluationratelimit";

    @Autowired
    private ApplicationContext applicationContext;

    private Object resource;

    private Object originalRateLimitService;

    private String endpoint;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 2);
        var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
        ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        endpoint = "/api/programming/programming-exercises/" + exercise.getId() + "/grading/re-evaluate";

        var properties = new RateLimitingProperties();
        properties.setEnabled(true);
        properties.setReEvaluationRequestsPerMinute(1);
        var features = mock(FeatureToggleService.class);
        when(features.isFeatureEnabled(Feature.RateLimit)).thenReturn(true);
        var limiter = new RateLimitService(new RateLimitConfig().localRateLimitProxyManager(), new RateLimitConfigurationService(properties), features);
        resource = AopTestUtils.getUltimateTargetObject(applicationContext.getBean("programmingExerciseGradingResource"));
        originalRateLimitService = ReflectionTestUtils.getField(resource, "rateLimitService");
        ReflectionTestUtils.setField(resource, "rateLimitService", limiter);
    }

    @AfterEach
    void restoreRateLimitService() {
        ReflectionTestUtils.setField(resource, "rateLimitService", originalRateLimitService);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void furtherReEvaluationsOfAUserAreRefusedOnceTheLimitIsUsedUp() throws Exception {
        request.putWithResponseBody(endpoint, "{}", Integer.class, HttpStatus.OK);

        request.put(endpoint, "{}", HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void theLimitOfOneUserDoesNotAffectAnotherUser() throws Exception {
        request.putWithResponseBody(endpoint, "{}", Integer.class, HttpStatus.OK);
        request.put(endpoint, "{}", HttpStatus.TOO_MANY_REQUESTS);
        userUtilService.changeUser(TEST_PREFIX + "instructor2");

        request.putWithResponseBody(endpoint, "{}", Integer.class, HttpStatus.OK);
    }
}
