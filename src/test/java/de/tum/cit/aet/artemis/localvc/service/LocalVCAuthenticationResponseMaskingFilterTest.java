package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for the filter that hides whether a git repository exists by giving every 401 the same shape. See
 * {@link LocalVCAuthenticationResponseMaskingFilter} and finding F-014 (repository enumeration via 401 vs 404).
 */
class LocalVCAuthenticationResponseMaskingFilterTest {

    private static final String CHALLENGE_HEADER = "WWW-Authenticate";

    @Test
    void masksSendErrorWithoutMessageAsAnEmptyChallenge() throws Exception {
        MockHttpServletResponse response = runWithChain((request, wrappedResponse) -> ((HttpServletResponse) wrappedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader(CHALLENGE_HEADER)).isEqualTo("Basic");
        assertThat(response.getContentAsString()).isEmpty();
        // A masked 401 must never reach the container error page, which would carry a body distinguishing it from the
        // authentication filters' 401.
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void masksSendErrorWithMessageAndDoesNotLeakIt() throws Exception {
        String leakyMessage = "Repository SOMEKEY/somekey-victim.git not found";

        MockHttpServletResponse response = runWithChain(
                (request, wrappedResponse) -> ((HttpServletResponse) wrappedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED, leakyMessage));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader(CHALLENGE_HEADER)).isEqualTo("Basic");
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void leavesOtherErrorStatusesUntouched() throws Exception {
        MockHttpServletResponse response = runWithChain(
                (request, wrappedResponse) -> ((HttpServletResponse) wrappedResponse).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "server failure"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        // No masking: the challenge header must not be added to a non-401 response, and the container keeps the message.
        assertThat(response.getHeader(CHALLENGE_HEADER)).isNull();
        assertThat(response.getErrorMessage()).isEqualTo("server failure");
    }

    @Test
    void passesTheRequestAndAWrappedResponseDownTheChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        LocalVCAuthenticationResponseMaskingFilter filter = new LocalVCAuthenticationResponseMaskingFilter();
        filter.doFilter(new MockHttpServletRequest("GET", "/git/SOMEKEY/somekey-student.git/info/refs"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("the filter must not swallow the request").isNotNull();
        assertThat(chain.getResponse()).as("the filter must pass a response down the chain").isNotNull();
    }

    private static MockHttpServletResponse runWithChain(FilterChain chain) throws Exception {
        LocalVCAuthenticationResponseMaskingFilter filter = new LocalVCAuthenticationResponseMaskingFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/git/SOMEKEY/somekey-student.git/info/refs"), response, chain);
        return response;
    }
}
