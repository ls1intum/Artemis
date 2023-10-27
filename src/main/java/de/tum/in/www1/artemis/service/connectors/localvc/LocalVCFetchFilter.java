package de.tum.in.www1.artemis.service.connectors.localvc;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.in.www1.artemis.exception.localvc.LocalVCAuthException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCForbiddenException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;

/**
 * Filters incoming fetch requests reaching the local git server implementation.
 */
public class LocalVCFetchFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(LocalVCFetchFilter.class);

    private final LocalVCServletService localVCServletService;

    public LocalVCFetchFilter(LocalVCServletService localVCServletService) {
        this.localVCServletService = localVCServletService;
    }

    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, @NotNull FilterChain filterChain) throws IOException, ServletException {

        log.debug("Trying to fetch repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        try {
            localVCServletService.authenticateAndAuthorizeGitRequest(servletRequest, RepositoryActionType.READ);
        }
        catch (LocalVCAuthException | LocalVCForbiddenException | LocalVCInternalException e) {
            servletResponse.setStatus(localVCServletService.getHttpStatusForException(e, servletRequest.getRequestURI()));
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
