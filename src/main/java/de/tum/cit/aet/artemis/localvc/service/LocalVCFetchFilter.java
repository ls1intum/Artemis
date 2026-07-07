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

    public LocalVCFetchFilter(LocalVCServletService localVCServletService) {
        this.localVCServletService = localVCServletService;
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
            // The first request of every git operation has no Authorization header by design (the client waits
            // for the 401 challenge), so do not log that expected handshake. Log every other rejection with its
            // concrete reason — otherwise a 401/403 is returned silently and is impossible to diagnose.
            if (!"No authorization header provided".equals(e.getMessage())) {
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

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
