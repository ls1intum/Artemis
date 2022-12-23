package de.tum.in.www1.artemis.security.localVC;

import java.io.IOException;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filters incoming fetch requests reaching the local git server implementation.
 */
public class LocalVCFetchFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(LocalVCFetchFilter.class);

    private final LocalVCFilterUtilService localVCFilterUtilService;

    public LocalVCFetchFilter(LocalVCFilterUtilService localVCFilterUtilService) {
        this.localVCFilterUtilService = localVCFilterUtilService;
    }

    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        log.debug("Trying to fetch repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        try {
            localVCFilterUtilService.authenticateAndAuthorizeGitRequest(servletRequest, false);
        }
        catch (LocalVCAuthException e) {
            servletResponse.setStatus(401);
            return;
        }
        catch (LocalVCBadRequestException e) {
            servletResponse.setStatus(400);
            return;
        }
        catch (LocalVCNotFoundException e) {
            servletResponse.setStatus(404);
            return;
        }
        catch (LocalVCInternalException e) {
            servletResponse.setStatus(500);
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
