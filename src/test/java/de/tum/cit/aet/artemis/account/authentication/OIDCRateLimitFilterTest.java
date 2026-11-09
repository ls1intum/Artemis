package de.tum.cit.aet.artemis.account.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import de.tum.cit.aet.artemis.account.security.OIDCRateLimitFilter;
import de.tum.cit.aet.artemis.admin.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.config.RateLimitConfig;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import inet.ipaddr.IPAddressString;

/**
 * Tests the OIDC authorization rate limit with a real local Bucket4j store, but without
 * starting an Artemis Spring application context. The existing
 * {@code OIDCAuthorizationRequestIntegrationTest} covers real OIDC redirects and storage.
 */
class OIDCRateLimitFilterTest {

    private RateLimitConfigurationService configurationService;

    private FeatureToggleService featureToggleService;

    private OIDCRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        configurationService = mock(RateLimitConfigurationService.class);
        featureToggleService = mock(FeatureToggleService.class);
        when(configurationService.isRateLimitingEnabled()).thenReturn(true);
        when(configurationService.getEffectiveRpm(RateLimitType.OIDC_AUTHORIZATION)).thenReturn(1);
        when(featureToggleService.isFeatureEnabled(Feature.RateLimit)).thenReturn(true);
        // Each test gets its own bucket store: no cross-test interference or shared Spring context.
        RateLimitService rateLimitService = new RateLimitService(new RateLimitConfig().localRateLimitProxyManager(), configurationService, featureToggleService);
        filter = new OIDCRateLimitFilter(rateLimitService);
    }

    @Test
    void firstAuthorizationRequestIsAllowed() throws Exception {
        var request = request("192.168.1.101");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void exceedingBudgetReturns429AndDoesNotContinueToAuthorizationRequestStorage() throws Exception {
        var request = request("192.168.1.102");
        var firstResponse = new MockHttpServletResponse();
        var rejectedResponse = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, firstResponse, chain);
        filter.doFilter(request("192.168.1.102"), rejectedResponse, chain);

        assertThat(rejectedResponse.getStatus()).isEqualTo(429);
        assertThat(rejectedResponse.getHeader("Retry-After")).isNotBlank();
        // The authorization redirect filter (and its repository save) is downstream.
        verify(chain).doFilter(request, firstResponse);
        verify(chain, never()).doFilter(any(), org.mockito.ArgumentMatchers.same(rejectedResponse));
    }

    @Test
    void exemptAddressDoesNotSpendItsBudget() throws Exception {
        var exempt = new IPAddressString("192.168.1.100").getAddress();
        when(configurationService.isExempt(exempt)).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);

        var firstResponse = new MockHttpServletResponse();
        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(request("192.168.1.100"), firstResponse, chain);
        filter.doFilter(request("192.168.1.100"), secondResponse, chain);

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
        verify(chain, org.mockito.Mockito.times(2)).doFilter(any(), any());
    }

    @Test
    void disabledGlobalPropertyBypassesInvalidRemoteAddress() throws Exception {
        when(configurationService.isRateLimitingEnabled()).thenReturn(false);
        assertBypassesInvalidAddress();
    }

    @Test
    void disabledRuntimeFeatureBypassesInvalidRemoteAddress() throws Exception {
        when(featureToggleService.isFeatureEnabled(Feature.RateLimit)).thenReturn(false);
        assertBypassesInvalidAddress();
    }

    @Test
    void invalidRemoteAddressReturns400WhenEnabled() throws Exception {
        var request = request("invalid-ip-format-string");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void addressWithPortIsAccepted() throws Exception {
        var request = request("192.168.1.105:54321");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void forwardedForHeaderCannotSelectAnotherBucket() throws Exception {
        var first = request("192.168.1.107");
        first.addHeader("X-Forwarded-For", "192.168.1.200");
        var second = request("192.168.1.107");
        second.addHeader("X-Forwarded-For", "192.168.1.201");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(first, new MockHttpServletResponse(), chain);
        var response = new MockHttpServletResponse();
        filter.doFilter(second, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void unrelatedEndpointIsNotRateLimitedOrIpValidated() throws Exception {
        var request = new MockHttpServletRequest("GET", "/login/oauth2/code/oidc");
        request.setRemoteAddr("invalid-ip-format-string");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void assertBypassesInvalidAddress() throws Exception {
        var request = request("invalid-ip-format-string");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/oidc");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
