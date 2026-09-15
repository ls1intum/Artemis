package de.tum.cit.aet.artemis.core.security.annotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Optional;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.admin.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.security.RateLimitKey;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import inet.ipaddr.IPAddress;
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

        verify(rateLimitService).enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.ACCOUNT_MANAGEMENT);
    }

    @Test
    void testAspect_WithClassLevelAnnotation_ShouldUseClassAnnotation() throws Throwable {
        Method method = ClassWithRateLimit.class.getMethod("methodInClassWithRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(rateLimitService.resolveClientId()).thenReturn(new IPAddressString("192.168.1.1").toAddress());

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService).enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.AUTHENTICATION);
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

        verify(rateLimitService).enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.AUTHENTICATION);
    }

    @Test
    void testAspect_WithUserKey_ShouldEnforcePerUser() throws Throwable {
        Method method = TestService.class.getMethod("methodWithUserKeyedRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        IPAddress clientAddress = new IPAddressString("192.168.1.1").toAddress();
        when(rateLimitService.resolveClientId()).thenReturn(clientAddress);

        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.of("alice"));

            aspect.checkRateLimit(joinPoint);
        }

        verify(rateLimitService).enforcePerMinute(clientAddress, "user:alice", RateLimitType.REPOSITORY_EDITOR);
    }

    @Test
    void testAspect_WithUserKey_NoAuthenticatedUser_ShouldFallBackToClientAddress() throws Throwable {
        Method method = TestService.class.getMethod("methodWithUserKeyedRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        IPAddress clientAddress = new IPAddressString("192.168.1.1").toAddress();
        when(rateLimitService.resolveClientId()).thenReturn(clientAddress);

        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserLogin).thenReturn(Optional.empty());

            aspect.checkRateLimit(joinPoint);
        }

        verify(rateLimitService).enforcePerMinute(clientAddress, RateLimitType.REPOSITORY_EDITOR);
    }

    @Test
    void testAspect_WithClassLevelAnnotationOnRuntimeTargetOnly_ShouldResolveViaTargetClass() throws Throwable {
        // The intercepted method is declared on a superclass that carries no annotation; only the concrete runtime
        // target class is annotated. This mirrors the repository editor controllers, whose limit sits on the class.
        Method method = BaseWithoutRateLimit.class.getMethod("inheritedMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new AnnotatedSubclass());
        when(rateLimitService.resolveClientId()).thenReturn(new IPAddressString("192.168.1.1").toAddress());

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService).enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.AUTHENTICATION);
    }

    public static class TestService {

        @LimitRequestsPerMinute(type = RateLimitType.ACCOUNT_MANAGEMENT)
        public void methodWithTypeBasedRateLimit() {
            // Test method
        }

        @LimitRequestsPerMinute // Uses default type (AUTHENTICATION)
        public void methodWithDefaultTypeRateLimit() {
            // Test method
        }

        @LimitRequestsPerMinute(type = RateLimitType.REPOSITORY_EDITOR, key = RateLimitKey.USER)
        public void methodWithUserKeyedRateLimit() {
            // Test method
        }

        public void methodWithoutRateLimit() {
            // Test method without rate limiting
        }
    }

    @LimitRequestsPerMinute(type = RateLimitType.AUTHENTICATION)
    public static class ClassWithRateLimit {

        public void methodInClassWithRateLimit() {
            // Test method
        }
    }

    public static class BaseWithoutRateLimit {

        public void inheritedMethod() {
            // Declared on the superclass, without any rate-limit annotation
        }
    }

    @LimitRequestsPerMinute(type = RateLimitType.AUTHENTICATION)
    public static class AnnotatedSubclass extends BaseWithoutRateLimit {
        // Inherits inheritedMethod() and carries the class-level annotation on the concrete type
    }
}
