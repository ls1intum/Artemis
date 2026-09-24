package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Normalises the 401 that JGit's repository resolver emits through {@link HttpServletResponse#sendError} so that it is
 * indistinguishable from the 401 the local VC authentication filters produce.
 * <p>
 * JGit's built-in repository filter resolves the repository before the authentication filters run, so a missing
 * repository is rejected by the resolver while an existing but forbidden one is rejected by the authentication filters.
 * {@link ArtemisGitServletService} already masks the missing repository as a 401, but the two 401 responses are produced
 * by different code: the authentication filters ({@link LocalVCFetchFilter} / {@link LocalVCPushFilter}) answer with
 * {@code setStatus(401)}, a {@code WWW-Authenticate: Basic} header and an empty body, whereas the resolver path reaches
 * the caller through {@link HttpServletResponse#sendError} and would otherwise carry a container error page and no
 * challenge header. That difference would move the repository-existence oracle from the status code onto the response
 * header and body, so this filter rewrites the {@code sendError} 401 to the exact shape the authentication filters
 * produce. A 401 emitted through {@code setStatus} (as the authentication filters already do) is left untouched, because
 * it is already in that shape.
 * <p>
 * Successful git responses stream their pack data through {@link HttpServletResponse#getOutputStream} and never call
 * {@code sendError}, so they are not affected. Only {@code sendError} is intercepted, which is exactly the resolver
 * error path.
 */
public class LocalVCAuthenticationResponseMaskingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, new MaskingResponseWrapper(response));
    }

    /**
     * Rewrites an {@code sendError(401, ...)} into the same response the LocalVC authentication filters produce for an
     * existing but forbidden repository, and leaves every other status untouched.
     */
    private static final class MaskingResponseWrapper extends HttpServletResponseWrapper {

        private MaskingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendError(int statusCode, String message) throws IOException {
            // The message would end up in the container error page body and could reveal the requested repository, so it
            // is dropped and the status-only overload decides how to answer.
            sendError(statusCode);
        }

        @Override
        public void sendError(int statusCode) throws IOException {
            if (statusCode == HttpServletResponse.SC_UNAUTHORIZED) {
                // Match the authentication filters: status 401, the Basic challenge header and an empty body. Not
                // delegating to super.sendError avoids the container error page whose body would otherwise reveal that
                // this 401 came from the repository resolver rather than from the authorization check.
                setHeader("WWW-Authenticate", "Basic");
                setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            super.sendError(statusCode);
        }
    }
}
