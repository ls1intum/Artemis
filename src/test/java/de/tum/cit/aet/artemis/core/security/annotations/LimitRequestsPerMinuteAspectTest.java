package de.tum.cit.aet.artemis.core.security.annotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.admin.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
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
    void testAspect_PerUserLimit_CountsTheSignedInUserWithoutRegardToTheClientAddress() throws Throwable {
        Method method = TestService.class.getMethod("methodWithPerUserRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("Student1", "pw", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        try {
            aspect.checkRateLimit(joinPoint);
        }
        finally {
            SecurityContextHolder.clearContext();
        }

        verify(rateLimitService).enforceBudget("user:student1", RateLimitType.APOLLON_CONVERSION);
        verify(rateLimitService, never()).enforcePerMinute(any(), any());
    }

    @Test
    void testAspect_PerUserLimit_IsNotEnforcedForAnExemptAddress() throws Throwable {
        Method method = TestService.class.getMethod("methodWithPerUserRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        var exemptAddress = new IPAddressString("192.168.1.1").toAddress();
        when(rateLimitService.resolveClientId()).thenReturn(exemptAddress);
        when(configurationService.isExempt(exemptAddress)).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("student1", "pw", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        try {
            aspect.checkRateLimit(joinPoint);
        }
        finally {
            SecurityContextHolder.clearContext();
        }

        verify(rateLimitService, never()).enforceBudget(any(), any());
        verify(rateLimitService, never()).enforcePerMinute(any(), any());
    }

    @Test
    void testAspect_PerUserLimit_FallsBackToTheClientAddressWithoutSignedInUser() throws Throwable {
        Method method = TestService.class.getMethod("methodWithPerUserRateLimit");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(rateLimitService.resolveClientId()).thenReturn(new IPAddressString("192.168.1.1").toAddress());
        SecurityContextHolder.clearContext();

        aspect.checkRateLimit(joinPoint);

        verify(rateLimitService).enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.APOLLON_CONVERSION);
    }

    public static class TestService {

        @LimitRequestsPerMinute(type = RateLimitType.APOLLON_CONVERSION, perUser = true)
        public void methodWithPerUserRateLimit() {
            // Test method
        }

        @LimitRequestsPerMinute(type = RateLimitType.ACCOUNT_MANAGEMENT)
        public void methodWithTypeBasedRateLimit() {
            // Test method
        }

        @LimitRequestsPerMinute // Uses default type (AUTHENTICATION)
        public void methodWithDefaultTypeRateLimit() {
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
}
