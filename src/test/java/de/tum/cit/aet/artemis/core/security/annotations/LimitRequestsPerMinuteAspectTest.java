package de.tum.cit.aet.artemis.core.security.annotations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.core.service.RateLimitService;

@ExtendWith(MockitoExtension.class)
class LimitRequestsPerMinuteAspectTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private RateLimitConfigurationService configurationService;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private LimitRequestsPerMinuteAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new LimitRequestsPerMinuteAspect(rateLimitService, configurationService);
    }

    @Test
    void testAspect_WithFixedValue_ShouldCallRateLimitService() throws Throwable {
        Method method = TestService.class.getMethod("methodWithFixedRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(configurationService.getEffectiveRpm(10)).thenReturn(10);
        when(rateLimitService.resolveClientId()).thenReturn("192.168.1.1");

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService).enforcePerMinute("192.168.1.1", 10);
    }

    @Test
    void testAspect_WithRateLimitType_ShouldCallRateLimitService() throws Throwable {
        Method method = TestService.class.getMethod("methodWithTypeBasedRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(configurationService.getEffectiveRpm(RateLimitType.PUBLIC)).thenReturn(5);
        when(rateLimitService.resolveClientId()).thenReturn("192.168.1.1");

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService).enforcePerMinute("192.168.1.1", 5);
    }

    @Test
    void testAspect_WithClassLevelAnnotation_ShouldUseClassAnnotation() throws Throwable {
        Method method = ClassWithRateLimit.class.getMethod("methodInClassWithRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(configurationService.getEffectiveRpm(RateLimitType.LOGIN_RELATED)).thenReturn(30);
        when(rateLimitService.resolveClientId()).thenReturn("192.168.1.1");

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService).enforcePerMinute("192.168.1.1", 30);
    }

    @Test
    void testAspect_WhenRateLimitExceeded_ShouldPropagateException() throws Throwable {
        Method method = TestService.class.getMethod("methodWithFixedRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(configurationService.getEffectiveRpm(10)).thenReturn(10);
        when(rateLimitService.resolveClientId()).thenReturn("192.168.1.1");
        doThrow(new RateLimitExceededException(30)).when(rateLimitService).enforcePerMinute(anyString(), anyInt());

        assertThatThrownBy(() -> aspect.checkRateLimit(joinPoint)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void testAspect_WithNoAnnotations_ShouldNotCallRateLimitService() throws Throwable {
        Method method = TestService.class.getMethod("methodWithoutRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService, never()).enforcePerMinute(anyString(), anyInt());
    }

    @Test
    void testAspect_WithTypeBasedAnnotation_DefaultValue_ShouldUseDefault() throws Throwable {
        Method method = TestService.class.getMethod("methodWithDefaultTypeRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(configurationService.getEffectiveRpm(RateLimitType.PUBLIC)).thenReturn(5); // Default value
        when(rateLimitService.resolveClientId()).thenReturn("192.168.1.1");

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService).enforcePerMinute("192.168.1.1", 5);
    }

    public static class TestService {

        @LimitRequestsPerMinute(value = 10)
        public void methodWithFixedRateLimit() {
            // Test method
        }

        @LimitRequestsPerMinute(type = RateLimitType.PUBLIC)
        public void methodWithTypeBasedRateLimit() {
            // Test method
        }

        @LimitRequestsPerMinute // Uses default type (PUBLIC)
        public void methodWithDefaultTypeRateLimit() {
            // Test method
        }

        public void methodWithoutRateLimit() {
            // Test method without rate limiting
        }
    }

    @LimitRequestsPerMinute(type = RateLimitType.LOGIN_RELATED)
    public static class ClassWithRateLimit {

        public void methodInClassWithRateLimit() {
            // Test method
        }
    }
}
