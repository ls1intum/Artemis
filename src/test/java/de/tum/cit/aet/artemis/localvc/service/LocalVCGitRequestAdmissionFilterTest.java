package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for the admission filter. A rejection must be answered directly and must not let the request reach the
 * repository resolver, and the two rejection shapes (401 vs 429) must not share headers.
 */
class LocalVCGitRequestAdmissionFilterTest {

    private static final String CHALLENGE_HEADER = "WWW-Authenticate";

    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final LocalVCServletService localVCServletService = mock(LocalVCServletService.class);

    private final LocalVCGitRequestAdmissionFilter filter = new LocalVCGitRequestAdmissionFilter(localVCServletService);

    private static MockHttpServletRequest handshake(String service) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/git/TEST/test-student.git/info/refs");
        if (service != null) {
            request.setParameter("service", service);
        }
        return request;
    }

    @Test
    void aMalformedRequestIsRejectedWith400WithoutAdmittingOrResolving() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(handshake(null), response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(chain.getRequest()).as("the resolver must not run for a malformed request").isNull();
        verify(localVCServletService, never()).admitGitRequest(any(), any());
    }

    @Test
    void anUnauthenticatedDecisionIsAnsweredWithAnEmptyBasicChallenge() throws Exception {
        when(localVCServletService.admitGitRequest(any(), any())).thenReturn(new GitHandshakeDecision.Unauthenticated(true));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(handshake("git-upload-pack"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(CHALLENGE_HEADER)).isEqualTo("Basic");
        assertThat(response.getHeader(RETRY_AFTER_HEADER)).isNull();
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void aRateLimitedDecisionIsAnsweredWith429AndRetryAfterButNoChallenge() throws Exception {
        when(localVCServletService.admitGitRequest(any(), any())).thenReturn(new GitHandshakeDecision.RateLimited(42));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(handshake("git-upload-pack"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(RETRY_AFTER_HEADER)).isEqualTo("42");
        assertThat(response.getHeader(CHALLENGE_HEADER)).as("a throttled response must not look like a missing credential").isNull();
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void anOrdinaryDecisionProceedsAndRecordsTheDecision() throws Exception {
        GitHandshakeDecision decision = new GitHandshakeDecision.OrdinaryAuthentication();
        when(localVCServletService.admitGitRequest(any(), any())).thenReturn(decision);
        MockHttpServletRequest request = handshake("git-upload-pack");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(LocalVCServletService.GIT_HANDSHAKE_DECISION_REQUEST_ATTRIBUTE)).isEqualTo(decision);
    }

    @Test
    void aBuildAgentDecisionProceedsAndRecordsTheDecision() throws Exception {
        GitHandshakeDecision decision = new GitHandshakeDecision.BuildAgentClone();
        when(localVCServletService.admitGitRequest(any(), any())).thenReturn(decision);
        MockHttpServletRequest request = handshake("git-upload-pack");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(LocalVCServletService.GIT_HANDSHAKE_DECISION_REQUEST_ATTRIBUTE)).isEqualTo(decision);
    }
}
