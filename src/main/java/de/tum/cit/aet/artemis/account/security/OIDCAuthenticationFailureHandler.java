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
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import de.tum.cit.aet.artemis.account.config.OIDCConstants;
import de.tum.cit.aet.artemis.account.config.OIDCEnabled;

/**
 * Custom failure handler for OIDC authentication exceptions.
 * Prevents Spring Security from failing silently and redirects the user to the login page with an error code.
 */
@Lazy
@Component
@Conditional(OIDCEnabled.class)
public class OIDCAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OIDCAuthenticationFailureHandler.class);

    private final TemplateEngine templateEngine;

    public OIDCAuthenticationFailureHandler(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

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
        String redirectTarget = null;
        HttpSession session = request.getSession(false);
        if (session != null) {
            redirectTarget = (String) session.getAttribute(OIDCConstants.OIDC_REDIRECT_TARGET_SESSION_KEY);
            session.invalidate();
        }
        // If user is not validated
        boolean isDeactivated = exception instanceof OAuth2AuthenticationException oauth2Exception && "user_deactivated".equals(oauth2Exception.getError().getErrorCode());
        String errorCode = isDeactivated ? "deactivated" : "oidcFailure";

        if (OIDCConstants.VS_CODE_REDIRECT_TARGET.equalsIgnoreCase(redirectTarget)) {
            String deepLink = OIDCConstants.VS_CODE_DEEP_LINK_BASE + "?error=" + errorCode;
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/html;charset=UTF-8");

            Context context = new Context();
            context.setVariable("deepLink", deepLink);
            context.setVariable("isError", true);
            context.setVariable("errorMessage", "Authentication failed: " + errorCode);

            String htmlContent = templateEngine.process("account/vscode-callback", context);
            response.getWriter().write(htmlContent);
            response.getWriter().flush();
        }
        else {
            response.sendRedirect("/sign-in?loginError=" + errorCode);
        }
    }
}
