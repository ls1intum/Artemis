package de.tum.cit.aet.artemis.core.security.annotations;

import static org.mockito.ArgumentMatchers.any;
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

import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.core.service.RateLimitService;
import inet.ipaddr.IPAddressString;

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
    void testAspect_WithRateLimitType_ShouldCallRateLimitService() throws Throwable {
        Method method = TestService.class.getMethod("methodWithTypeBasedRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(rateLimitService.resolveClientId()).thenReturn(new IPAddressString("192.168.1.1").toAddress());

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService).enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.PUBLIC);
    }

    @Test
    void testAspect_WithClassLevelAnnotation_ShouldUseClassAnnotation() throws Throwable {
        Method method = ClassWithRateLimit.class.getMethod("methodInClassWithRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(rateLimitService.resolveClientId()).thenReturn(new IPAddressString("192.168.1.1").toAddress());

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService).enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.LOGIN_RELATED);
    }

    @Test
    void testAspect_WithNoAnnotations_ShouldNotCallRateLimitService() throws Throwable {
        Method method = TestService.class.getMethod("methodWithoutRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService, never()).enforcePerMinute(any(), any());
    }

    @Test
    void testAspect_WithTypeBasedAnnotation_DefaultValue_ShouldUseDefault() throws Throwable {
        Method method = TestService.class.getMethod("methodWithDefaultTypeRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(rateLimitService.resolveClientId()).thenReturn(new IPAddressString("192.168.1.1").toAddress());

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService).enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.PUBLIC);
    }

    public static class TestService {

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
