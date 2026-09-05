package de.tum.cit.aet.artemis.account.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import de.tum.cit.aet.artemis.account.config.OIDCConstants;
import de.tum.cit.aet.artemis.account.config.OIDCEnabled;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.ArtemisSuccessfulLoginService;
import de.tum.cit.aet.artemis.account.service.OIDCExchangeCodeService;
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.util.HttpRequestUtils;

@Lazy
@Component
@Conditional(OIDCEnabled.class)
public class OIDCAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JWTCookieService jwtCookieService;

    private final OIDCExchangeCodeService oidcExchangeCodeService;

    private final UserRepository userRepository;

    private final ArtemisSuccessfulLoginService artemisSuccessfulLoginService;

    private final TemplateEngine templateEngine;

    @Value("${artemis.user-management.oidc.mappings.username:preferred_username}")
    private String usernameClaimKey;

    public OIDCAuthenticationSuccessHandler(JWTCookieService jwtCookieService, UserRepository userRepository, ArtemisSuccessfulLoginService artemisSuccessfulLoginService,
            OIDCExchangeCodeService oidcExchangeCodeService, TemplateEngine templateEngine) {
        this.jwtCookieService = jwtCookieService;
        this.oidcExchangeCodeService = oidcExchangeCodeService;
        this.userRepository = userRepository;
        this.artemisSuccessfulLoginService = artemisSuccessfulLoginService;
        this.templateEngine = templateEngine;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        boolean rememberMe = false;
        String redirectTarget = null;
        String codeChallenge = null;
        HttpSession session = request.getSession(false);
        if (session != null) {
            Boolean storedRememberMe = (Boolean) session.getAttribute(OIDCConstants.OIDC_REMEMBER_ME_SESSION_KEY);
            if (storedRememberMe != null) {
                rememberMe = storedRememberMe;
            }
            redirectTarget = (String) session.getAttribute(OIDCConstants.OIDC_REDIRECT_TARGET_SESSION_KEY);
            codeChallenge = (String) session.getAttribute(OIDCConstants.OIDC_CODE_CHALLENGE_SESSION_KEY);
        }

        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String username = oidcUser.getAttribute(usernameClaimKey);
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("OIDC authentication succeeded but required username claim '" + usernameClaimKey + "' is missing.");
        }

        User user = userRepository.findOneWithAuthoritiesByLogin(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated OIDC user " + username + " could not be found in the database."));

        var authorities = user.getAuthorities().stream().map(authority -> new SimpleGrantedAuthority(authority.getName())).toList();
        UsernamePasswordAuthenticationToken artemisAuth = new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), authorities);
        SecurityContextHolder.getContext().setAuthentication(artemisAuth);

        ResponseCookie jwtCookie = jwtCookieService.buildLoginCookie(rememberMe);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        artemisSuccessfulLoginService.sendLoginEmail(user.getLogin(), AuthenticationMethod.OIDC, HttpRequestUtils.getClientEnvironment(request));

        if (session != null) {
            session.invalidate();
        }

        // Handle redirect based on parameter: generate code strictly for recognized VS Code client
        if (OIDCConstants.VS_CODE_REDIRECT_TARGET.equalsIgnoreCase(redirectTarget)) {
            // If code challenge is invalid, then reject the native redirect request
            if (!oidcExchangeCodeService.isValidCodeChallenge(codeChallenge)) {
                renderCallbackPage(response, OIDCConstants.VS_CODE_DEEP_LINK_BASE + "?error=invalid_request", true, "Invalid authentication request parameters.");
                return;
            }

            String jwtToken = jwtCookie.getValue();
            String exchangeCode = oidcExchangeCodeService.storeJwtAndGenerateCode(jwtToken, codeChallenge);
            if (exchangeCode == null) {
                renderCallbackPage(response, OIDCConstants.VS_CODE_DEEP_LINK_BASE + "?error=server_error", true, "Could not generate authorization exchange code.");
                return;
            }

            String vscodeDeepLink = OIDCConstants.VS_CODE_DEEP_LINK_BASE + "?code=" + exchangeCode;
            renderCallbackPage(response, vscodeDeepLink, false, null);
        }
        else if (OIDCConstants.IOS_REDIRECT_TARGET.equalsIgnoreCase(redirectTarget)) {
            if (!oidcExchangeCodeService.isValidCodeChallenge(codeChallenge)) {
                response.sendRedirect(OIDCConstants.IOS_DEEP_LINK_BASE + "?error=invalid_request");
                return;
            }

            String jwtToken = jwtCookie.getValue();
            String exchangeCode = oidcExchangeCodeService.storeJwtAndGenerateCode(jwtToken, codeChallenge);
            if (exchangeCode == null) {
                response.sendRedirect(OIDCConstants.IOS_DEEP_LINK_BASE + "?error=server_error");
                return;
            }

            response.sendRedirect(OIDCConstants.IOS_DEEP_LINK_BASE + "?code=" + exchangeCode);
        }
        else {
            response.sendRedirect("/");
        }
    }

    /**
     * Renders a 200 OK HTML landing page that launches the custom URI scheme via JavaScript.
     * Prevents Service Worker fetch failures caused by HTTP 302 redirects to non-HTTP protocols.
     */
    private void renderCallbackPage(HttpServletResponse response, String deepLink, boolean isError, String errorMessage) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");

        Context context = new Context();
        context.setVariable("deepLink", deepLink);
        context.setVariable("isError", isError);
        context.setVariable("errorMessage", errorMessage);

        String htmlContent = templateEngine.process("account/vscode-callback", context);
        response.getWriter().write(htmlContent);
        response.getWriter().flush();
    }
}
