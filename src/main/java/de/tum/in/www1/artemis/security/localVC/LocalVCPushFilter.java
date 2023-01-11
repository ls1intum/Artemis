package de.tum.in.www1.artemis.security.localVC;

import java.io.IOException;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filters incoming push requests reaching the local Version Control implementation.
 */
public class LocalVCPushFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(LocalVCPushFilter.class);

    private final LocalVCFilterUtilService localVCFilterUtilService;

    public LocalVCPushFilter(LocalVCFilterUtilService localVCFilterUtilService) {
        this.localVCFilterUtilService = localVCFilterUtilService;
    }

    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        log.debug("Trying to push to repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        try {
            localVCFilterUtilService.authenticateAndAuthorizeGitRequest(servletRequest, true);
        }
        catch (LocalVCAuthException e) {
            servletResponse.setStatus(401);
            return;
        }
        catch (LocalVCForbiddenException e) {
            servletResponse.setStatus(403);
            return;
        }
        catch (LocalVCBadRequestException e) {
            servletResponse.setStatus(400);
            return;
        }
        catch (LocalVCInternalException e) {
            servletResponse.setStatus(500);
            return;
        }

        // TODO: Notify Artemis on Push

        filterChain.doFilter(servletRequest, servletResponse);

    }
}
