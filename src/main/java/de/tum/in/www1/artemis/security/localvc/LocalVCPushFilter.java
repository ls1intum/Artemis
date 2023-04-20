package de.tum.in.www1.artemis.security.localvc;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.in.www1.artemis.exception.localvc.LocalVCAuthException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCForbiddenException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

/**
 * Filters incoming push requests reaching the local Version Control implementation.
 */
public class LocalVCPushFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(LocalVCPushFilter.class);

    private final LocalVCFilterService localVCFilterService;

    public LocalVCPushFilter(LocalVCFilterService localVCFilterService) {
        this.localVCFilterService = localVCFilterService;
    }

    /**
     * Filters incoming push requests performing authentication and authorization.
     */
    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, @NotNull FilterChain filterChain) throws IOException, ServletException {

        log.debug("Trying to push to repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        try {
            localVCFilterService.authenticateAndAuthorizeGitRequest(servletRequest, RepositoryActionType.WRITE);
        }
        catch (LocalVCAuthException | LocalVCForbiddenException | LocalVCInternalException e) {
            servletResponse.setStatus(localVCFilterService.getHttpStatusForException(e, servletRequest.getRequestURI()));
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);

    }
}
