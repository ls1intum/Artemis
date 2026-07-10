package de.tum.cit.aet.artemis.account.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.account.config.OIDCEnabled;

/**
 * Custom failure handler for OIDC authentication exceptions.
 * Prevents Spring Security from failing silently and redirects the user to the login page with an error code.
 */
@Lazy
@Component
@Conditional(OIDCEnabled.class)
public class OIDCAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final Logger log = LoggerFactory.getLogger(OIDCAuthenticationFailureHandler.class);

    /**
     * Handles OIDC authentication failures by logging the exception and redirecting the client to the sign-in page.
     *
     * @param request   the request during which the authentication failure occurred.
     * @param response  the response according to which the error routing is handled.
     * @param exception the exception that was thrown during the authentication process.
     * @throws IOException      if priority redirect fails due to an I/O error.
     * @throws ServletException if the servlet detects an unhandled failure.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        log.error("OIDC authentication failed: {}", exception.getMessage(), exception);
        // If user is not validated
        if (exception instanceof OAuth2AuthenticationException oauth2Exception && "user_deactivated".equals(oauth2Exception.getError().getErrorCode())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect("/sign-in?loginError=deactivated");
        }
        else {
            response.sendRedirect("/sign-in?loginError=oidcFailure");
        }
    }
}
