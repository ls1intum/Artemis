package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.grid.hazelcast.HazelcastProxyManager;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private HazelcastProxyManager<String> proxyManager;

    @Mock
    private Environment environment;

    @Mock
    private RateLimitConfigurationService configurationService;

    @Mock
    private BucketProxy bucketProxy;

    @Mock
    private ConsumptionProbe consumptionProbe;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private RequestAttributes requestAttributes;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(proxyManager, environment, configurationService);
    }

    @Test
    void testEnforcePerMinute_WhenRateLimitingDisabled_ShouldSkip() {
        when(configurationService.isRateLimitingEnabled()).thenReturn(false);

        rateLimitService.enforcePerMinute("192.168.1.1", 5);

        // Verify no bucket operations were performed
        verify(proxyManager, never()).getProxy(anyString(), any());
    }

    @Test
    void testEnforcePerMinute_WhenTestProfile_ShouldSkip() {
        when(configurationService.isRateLimitingEnabled()).thenReturn(true);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        rateLimitService.enforcePerMinute("192.168.1.1", 5);

        // Verify no bucket operations were performed
        verify(proxyManager, never()).getProxy(anyString(), any());
    }

    @Test
    void testEnforcePerMinute_WhenWithinLimit_ShouldSucceed() {
        when(configurationService.isRateLimitingEnabled()).thenReturn(true);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        when(proxyManager.getProxy(anyString(), any())).thenAnswer(invocation -> bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(4L);

        rateLimitService.enforcePerMinute("192.168.1.1", 5);

        // Verify bucket was used
        verify(bucketProxy).tryConsumeAndReturnRemaining(1);
    }

    @Test
    void testEnforcePerMinute_WhenExceedsLimit_ShouldThrowException() {
        when(configurationService.isRateLimitingEnabled()).thenReturn(true);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        when(proxyManager.getProxy(anyString(), any())).thenAnswer(invocation -> bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(false);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(30_000_000_000L); // 30 seconds

        assertThatThrownBy(() -> rateLimitService.enforcePerMinute("192.168.1.1", 5)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void testResolveClientId_WithXForwardedFor_ShouldUseFirstIp() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1:8080, 10.0.0.1");

            String clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo("192.168.1.1");
        }
    }

    @Test
    void testResolveClientId_WithRemoteAddr_ShouldCleanupIp() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.1.1:9090");

            String clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo("192.168.1.1");
        }
    }

    @Test
    void testResolveClientId_WithNoRequest_ShouldReturnUnknown() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

            String clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo("unknown");
        }
    }

    @Test
    void testIpCleanup_IPv4WithPort_ShouldRemovePort() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100:8080");

            String clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo("192.168.1.100");
        }
    }

    @Test
    void testIpCleanup_IPv4WithoutPort_ShouldReturnAsIs() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

            String clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo("10.0.0.1");
        }
    }

    @Test
    void testIpCleanup_IPv6WithBracketsAndPort_ShouldRemoveBracketsAndPort() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("[::1]:8080");

            String clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo("::1");
        }
    }

    @Test
    void testIpCleanup_IPv6WithoutBrackets_ShouldReturnAsIs() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("2001:db8::1");

            String clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo("2001:db8::1");
        }
    }

    @Test
    void testIpCleanup_InvalidFormat_ShouldReturnTrimmed() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("  invalid-ip-format  ");

            String clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo("invalid-ip-format");
        }
    }

    @Test
    void testIpCleanup_EmptyString_ShouldReturnUnknown() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("");
            when(httpServletRequest.getRemoteAddr()).thenReturn(null);

            String clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo("unknown");
        }
    }
}
