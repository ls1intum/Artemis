package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Filters incoming push requests reaching the local Version Control implementation.
 */
public class LocalVCPushFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LocalVCPushFilter.class);

    private final LocalVCServletService localVCServletService;

    private final LocalVCUsageTrackingService usageTrackingService;

    public LocalVCPushFilter(LocalVCServletService localVCServletService, LocalVCUsageTrackingService usageTrackingService) {
        this.localVCServletService = localVCServletService;
        this.usageTrackingService = usageTrackingService;
    }

    /**
     * Filters incoming push requests performing authentication and authorization.
     */
    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, @NonNull FilterChain filterChain) throws IOException, ServletException {
        log.debug("Trying to push to repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        try {
            localVCServletService.authenticateAndAuthorizeGitRequest(servletRequest, RepositoryActionType.WRITE);
        }
        catch (LocalVCAuthException | LocalVCForbiddenException | LocalVCInternalException e) {
            servletResponse.setStatus(localVCServletService.getHttpStatusForException(e, servletRequest.getRequestURI()));
            return;
        }

        long startNanos = System.nanoTime();
        // stays true if doFilter throws, so a push that blew up is not counted as a success
        boolean failed = true;
        try {
            filterChain.doFilter(servletRequest, servletResponse);
            failed = servletResponse.getStatus() >= HttpServletResponse.SC_BAD_REQUEST;
        }
        finally {
            usageTrackingService.recordPush(servletRequest, (System.nanoTime() - startNanos) / 1_000_000, failed);
        }
    }
}
