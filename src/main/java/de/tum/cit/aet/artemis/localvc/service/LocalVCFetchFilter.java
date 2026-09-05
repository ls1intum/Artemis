package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Filters incoming fetch requests reaching the local git server implementation.
 */
public class LocalVCFetchFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LocalVCFetchFilter.class);

    private final LocalVCServletService localVCServletService;

    private final LocalVCUsageTrackingService usageTrackingService;

    public LocalVCFetchFilter(LocalVCServletService localVCServletService, LocalVCUsageTrackingService usageTrackingService) {
        this.localVCServletService = localVCServletService;
        this.usageTrackingService = usageTrackingService;
    }

    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, @NonNull FilterChain filterChain) throws IOException, ServletException {

        log.debug("Trying to fetch repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        try {
            localVCServletService.authenticateAndAuthorizeGitRequest(servletRequest, RepositoryActionType.READ);
        }
        catch (LocalVCAuthException | LocalVCForbiddenException | LocalVCInternalException e) {
            int status = localVCServletService.getHttpStatusForException(e, servletRequest.getRequestURI());
            // Parts of the git authentication handshake are expected and happen on every clone, so they must not be
            // logged as warnings. The exception itself says whether it is such a case; matching on the message text
            // silently missed the second one (an empty password) and made every clone look like a failure. Log every
            // other rejection with its concrete reason, otherwise a 401/403 is returned silently and cannot be diagnosed.
            if (!(e instanceof LocalVCAuthException authException && authException.isExpectedDuringHandshake())) {
                log.warn("LocalVC fetch rejected for {} -> HTTP {} ({}: {})", servletRequest.getRequestURI(), status, e.getClass().getSimpleName(), e.getMessage());
            }
            servletResponse.setStatus(status);
            return;
        }
        catch (AuthenticationException e) {
            // intercept failed authentication to log it in the VCS access log
            log.warn("LocalVC fetch authentication failed for {} ({}: {})", servletRequest.getRequestURI(), e.getClass().getSimpleName(), e.getMessage());
            localVCServletService.createVCSAccessLogForFailedAuthenticationAttempt(servletRequest);
            throw e;
        }

        long startNanos = System.nanoTime();
        // stays true if doFilter throws, so a fetch that blew up is not counted as a success
        boolean failed = true;
        try {
            filterChain.doFilter(servletRequest, servletResponse);
            failed = servletResponse.getStatus() >= HttpServletResponse.SC_BAD_REQUEST;
        }
        finally {
            usageTrackingService.recordFetch(servletRequest, (System.nanoTime() - startNanos) / 1_000_000, failed);
        }
    }
}
