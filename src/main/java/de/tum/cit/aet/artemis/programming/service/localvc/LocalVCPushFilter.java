package de.tum.cit.aet.artemis.programming.service.localvc;

import java.io.IOException;
import java.util.function.IntUnaryOperator;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCAuthException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Filters incoming push requests reaching the local Version Control implementation.
 */
public class LocalVCPushFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LocalVCPushFilter.class);

    private final LocalVCServletService localVCServletService;

    private final IntUnaryOperator callbck;

    public LocalVCPushFilter(LocalVCServletService localVCServletService, IntUnaryOperator op) {
        this.localVCServletService = localVCServletService;
        this.callbck = op;
    }

    /**
     * Filters incoming push requests performing authentication and authorization.
     */
    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, @NotNull FilterChain filterChain) throws IOException, ServletException {

        log.debug("Trying to push to repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        try {
            localVCServletService.authenticateAndAuthorizeGitRequest(servletRequest, RepositoryActionType.WRITE);
        }
        catch (LocalVCAuthException | LocalVCForbiddenException | LocalVCInternalException e) {
            servletResponse.setStatus(localVCServletService.getHttpStatusForException(e, servletRequest.getRequestURI()));
            return;
        }

        // We need to extract the content of the request here as it is garbage collected before it can be used asynchronously
        String authorizationHeader = servletRequest.getHeader(LocalVCServletService.AUTHORIZATION_HEADER);
        String method = servletRequest.getMethod();
        var vcsAccessLog = servletRequest.getAttribute(HttpsConstants.VCS_ACCESS_LOG_KEY);
        if (vcsAccessLog instanceof VcsAccessLog accessLog) {
            this.localVCServletService.updateAndStoreVCSAccessLogForPushHTTPS(method, authorizationHeader, accessLog);
        }
        this.callbck.applyAsInt(4);
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
