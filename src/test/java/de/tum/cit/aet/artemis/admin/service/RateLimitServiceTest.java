package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.grid.hazelcast.HazelcastProxyManager;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private HazelcastProxyManager<String> proxyManager;

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

    @Mock
    private FeatureToggleService featureToggleService;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(proxyManager, configurationService, featureToggleService);
    }

    @Test
    void testEnforcePerMinute_WhenRateLimitingDisabled_ShouldSkip() throws AddressStringException {
        when(configurationService.isRateLimitingEnabled()).thenReturn(false);

        rateLimitService.enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.ACCOUNT_MANAGEMENT);

        // Verify no bucket operations were performed
        verify(proxyManager, never()).getProxy(anyString(), any());
    }

    @Test
    void testEnforcePerMinute_ClientErrorReport_IsEnforcedWhenRateLimitingIsDisabled() throws AddressStringException {
        // neither the global switch nor the feature toggle is consulted for a limit that cannot be disabled, so they are not stubbed
        when(configurationService.getEffectiveRpm(RateLimitType.CLIENT_ERROR_REPORT)).thenReturn(60);
        when(proxyManager.getProxy(anyString(), any())).thenReturn(bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(false);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(30_000_000_000L);
        IPAddress clientId = new IPAddressString("192.168.1.1").toAddress();

        assertThatThrownBy(() -> rateLimitService.enforcePerMinute(clientId, RateLimitType.CLIENT_ERROR_REPORT)).isInstanceOf(RateLimitExceededException.class);

        verify(configurationService, never()).isRateLimitingEnabled();
    }

    @Test
    void testEnforcePerMinute_ClientErrorReport_IsEnforcedWhenTheFeatureIsToggledOff() throws AddressStringException {
        when(configurationService.getEffectiveRpm(RateLimitType.CLIENT_ERROR_REPORT)).thenReturn(60);
        when(proxyManager.getProxy(anyString(), any())).thenReturn(bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);

        rateLimitService.enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.CLIENT_ERROR_REPORT);

        verify(bucketProxy).tryConsumeAndReturnRemaining(1);
        verify(featureToggleService, never()).isFeatureEnabled(any());
    }

    @Test
    void testEnforcePerMinute_ClientErrorReport_DoesNotLimitAnExemptAddress() throws AddressStringException {
        IPAddress clientId = new IPAddressString("192.168.1.1").toAddress();
        when(configurationService.isExempt(clientId)).thenReturn(true);

        rateLimitService.enforcePerMinute(clientId, RateLimitType.CLIENT_ERROR_REPORT);

        verify(proxyManager, never()).getProxy(anyString(), any());
    }

    @Test
    void testEnforcePerMinute_OtherTypes_AreNotEnforcedWhenTheFeatureIsToggledOff() throws AddressStringException {
        when(configurationService.isRateLimitingEnabled()).thenReturn(true);
        when(featureToggleService.isFeatureEnabled(any())).thenReturn(false);

        rateLimitService.enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.AUTHENTICATION);

        verify(proxyManager, never()).getProxy(anyString(), any());
    }

    @Test
    void testEnforcePerMinute_WhenWithinLimit_ShouldSucceed() throws AddressStringException {
        when(configurationService.isRateLimitingEnabled()).thenReturn(true);
        when(featureToggleService.isFeatureEnabled(any())).thenReturn(true);
        when(configurationService.getEffectiveRpm(RateLimitType.ACCOUNT_MANAGEMENT)).thenReturn(5);
        when(proxyManager.getProxy(anyString(), any())).thenReturn(bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(4L);

        rateLimitService.enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.ACCOUNT_MANAGEMENT);

        verify(bucketProxy).tryConsumeAndReturnRemaining(1);
    }

    @Test
    void testEnforcePerMinute_WhenExceedsLimit_ShouldThrowException() {
        when(configurationService.isRateLimitingEnabled()).thenReturn(true);
        when(featureToggleService.isFeatureEnabled(any())).thenReturn(true);
        when(configurationService.getEffectiveRpm(RateLimitType.ACCOUNT_MANAGEMENT)).thenReturn(5);
        when(proxyManager.getProxy(anyString(), any())).thenReturn(bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(false);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(30_000_000_000L);

        assertThatThrownBy(() -> rateLimitService.enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.ACCOUNT_MANAGEMENT))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void testEnforcePerMinute_TwoTypesWithSameRpm_ShouldUseSeparateBuckets() throws AddressStringException {
        when(configurationService.isRateLimitingEnabled()).thenReturn(true);
        when(featureToggleService.isFeatureEnabled(any())).thenReturn(true);
        // Both types yield the same RPM; without the fix they would share a bucket.
        when(configurationService.getEffectiveRpm(RateLimitType.ACCOUNT_MANAGEMENT)).thenReturn(30);
        when(configurationService.getEffectiveRpm(RateLimitType.AUTHENTICATION)).thenReturn(30);
        when(proxyManager.getProxy(anyString(), any())).thenReturn(bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);

        IPAddress clientId = new IPAddressString("192.168.1.1").toAddress();
        rateLimitService.enforcePerMinute(clientId, RateLimitType.ACCOUNT_MANAGEMENT);
        rateLimitService.enforcePerMinute(clientId, RateLimitType.AUTHENTICATION);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(proxyManager, times(2)).getProxy(keyCaptor.capture(), any());
        assertThat(keyCaptor.getAllValues()).hasSize(2);
        assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
        assertThat(keyCaptor.getAllValues().get(0)).contains(RateLimitType.ACCOUNT_MANAGEMENT.name());
        assertThat(keyCaptor.getAllValues().get(1)).contains(RateLimitType.AUTHENTICATION.name());
    }

    @Test
    void testEnforcePerMinute_EvenIfSpringTestProfile_ShouldStillEnforce() throws AddressStringException {
        when(configurationService.isRateLimitingEnabled()).thenReturn(true);
        when(featureToggleService.isFeatureEnabled(any())).thenReturn(true);
        when(configurationService.getEffectiveRpm(RateLimitType.ACCOUNT_MANAGEMENT)).thenReturn(5);
        when(proxyManager.getProxy(anyString(), any())).thenReturn(bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);

        rateLimitService.enforcePerMinute(new IPAddressString("192.168.1.1").toAddress(), RateLimitType.ACCOUNT_MANAGEMENT);

        verify(bucketProxy).tryConsumeAndReturnRemaining(1);
    }

    @Test
    void testResolveClientId_WithXForwardedFor_ShouldUseFirstIp() throws AddressStringException {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1, 10.0.0.1");

            IPAddress clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo(new IPAddressString("192.168.1.1").toAddress());
        }
    }

    @Test
    void testResolvePeerId_IgnoresForwardingHeadersSoASenderCannotChooseItsOwnBudget() throws AddressStringException {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getRemoteAddr()).thenReturn("203.0.113.7");

            IPAddress peerId = rateLimitService.resolvePeerId();

            assertThat(peerId).isEqualTo(new IPAddressString("203.0.113.7").toAddress());
            // no header is read at all, so there is nothing that a sender could set to change the address
            verify(httpServletRequest, never()).getHeader(anyString());
        }
    }

    @Test
    void testResolvePeerId_WithoutARequestOrAnAddressItCanParse_ShouldReturnNull() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(null);
            assertThat(rateLimitService.resolvePeerId()).isNull();
        }
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getRemoteAddr()).thenReturn("not an address");
            assertThat(rateLimitService.resolvePeerId()).isNull();
        }
    }

    @Test
    void testRejectionIsLoggedExceptForTheLimitThatBoundsTheLog() {
        assertThat(RateLimitType.CLIENT_ERROR_REPORT.isRejectionLogged()).isFalse();
        for (RateLimitType type : RateLimitType.values()) {
            if (type != RateLimitType.CLIENT_ERROR_REPORT) {
                assertThat(type.isRejectionLogged()).as(type.name()).isTrue();
            }
        }
    }

    @Test
    void testResolveClientId_Ipv6() throws AddressStringException {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("::1");

            IPAddress clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isEqualTo(new IPAddressString("::1").toAddress());
        }
    }

    @Test
    void testIpCleanup_InvalidFormat_ShouldReturnUntrimmedHeader() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("  invalid-ip-format  ");

            IPAddress clientId = rateLimitService.resolveClientId();

            assertThat(clientId).isNull();
        }
    }

    @Test
    void testIpCleanup_EmptyStringHeader_ShouldReturnNull() {
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = Mockito.mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.resolveReference(RequestAttributes.REFERENCE_REQUEST)).thenReturn(httpServletRequest);
            when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("unknown");
            when(httpServletRequest.getRemoteAddr()).thenReturn(null);

            IPAddress clientId = rateLimitService.resolveClientId();

            assertThat(clientId).matches(IPAddress::isLoopback);
        }
    }
}
