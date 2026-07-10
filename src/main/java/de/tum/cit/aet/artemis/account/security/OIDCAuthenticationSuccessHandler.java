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

import de.tum.cit.aet.artemis.account.config.OIDCEnabled;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;

@Lazy
@Component
@Conditional(OIDCEnabled.class)
public class OIDCAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JWTCookieService jwtCookieService;

    private final UserRepository userRepository;

    @Value("${artemis.user-management.oidc.mappings.username:preferred_username}")
    private String usernameClaimKey;

    public OIDCAuthenticationSuccessHandler(JWTCookieService jwtCookieService, UserRepository userRepository) {
        this.jwtCookieService = jwtCookieService;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        boolean rememberMe = false;
        HttpSession session = request.getSession(false);
        if (session != null) {
            // Extract stored rememberMe field from session
            Boolean storedRememberMe = (Boolean) session.getAttribute("OIDC_REMEMBER_ME");
            if (storedRememberMe != null) {
                rememberMe = storedRememberMe;
            }
        }
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String username = oidcUser.getAttribute(usernameClaimKey);
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("OIDC authentication succeeded but required username claim '" + usernameClaimKey + "' is missing.");
        }

        User user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated OIDC user " + username + " could not be found in the database."));

        // Artemis-side authorization, get roles from database
        var authorities = user.getAuthorities().stream().map(authority -> new SimpleGrantedAuthority(authority.getName())).toList();

        // Generate Artemis authentication token
        UsernamePasswordAuthenticationToken artemisAuth = new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), authorities);

        // Generate JWT String
        SecurityContextHolder.getContext().setAuthentication(artemisAuth);

        ResponseCookie jwtCookie = jwtCookieService.buildLoginCookie(rememberMe);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        // Remove state from OIDCConfiguration
        if (session != null) {
            session.invalidate();
        }

        // Redirect user to /courses page
        response.sendRedirect("/");
    }
}
