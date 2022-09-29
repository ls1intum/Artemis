package de.tum.in.www1.artemis.config.lti;

import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import de.tum.in.www1.artemis.security.lti.Lti13LaunchFilter;
import de.tum.in.www1.artemis.security.lti.Lti13RedirectProxyFilter;
import de.tum.in.www1.artemis.service.connectors.Lti13Service;
import uk.ac.ox.ctl.lti13.Lti13Configurer;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.authentication.OidcLaunchFlowAuthenticationProvider;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OAuth2LoginAuthenticationFilter;

/**
 * Configures and registers Security Filters to handle LTI 1.3 Resource Link Launches
 */
@Component
public class CustomLti13Configurer extends Lti13Configurer {

    public static final String LTI_BASE_PATH = "/api/lti13";

    public static final String LOGIN_INITIATION_PATH = "/initiate-login";

    public static final String LOGIN_REDIRECT_PROXY_PATH = "/auth-callback";

    public static final String LOGIN_PATH = "/auth-login";

    public static final String LOGIN_REDIRECT_CLIENT_PATH = "/lti/launch";

    @Override
    public void configure(HttpSecurity http) {
        setupSuperClass();

        AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository = configureRequestRepository();

        // Step 1 of the IMS SEC
        // https://www.imsglobal.org/spec/security/v1p0/#step-1-third-party-initiated-login
        // login initiation is handled by spring-security-lti13 completely
        http.addFilterAfter(this.configureInitiationFilter(clientRegistrationRepository(http), authorizationRequestRepository), LogoutFilter.class);

        // Step 3 of the IMS SEC
        // https://www.imsglobal.org/spec/security/v1p0/#step-3-authentication-response

        // A proxy is installed in order to redirect a Oidc Authorization response
        // back to the client if it was POSTed directly to the server.
        http.addFilterBefore(new Lti13RedirectProxyFilter(LTI_BASE_PATH + LOGIN_REDIRECT_PROXY_PATH, LOGIN_REDIRECT_CLIENT_PATH), LogoutFilter.class);

        // Anonymous (unauthenticated) LTI login requests are not supported.
        // Because of that, the Lti13LaunchFilter is placed in the filter chain after any other login filter (e.g. before AnonymousAuthenticationFilter)
        OidcLaunchFlowAuthenticationProvider oidcLaunchFlowAuthenticationProvider = configureAuthenticationProvider(http);
        OAuth2LoginAuthenticationFilter defaultLoginFilter = configureLoginFilter(clientRegistrationRepository(http), oidcLaunchFlowAuthenticationProvider,
                authorizationRequestRepository);
        http.addFilterBefore(new Lti13LaunchFilter(defaultLoginFilter, LTI_BASE_PATH + LOGIN_PATH, lti13Service(http)), AnonymousAuthenticationFilter.class);
    }

    protected void setupSuperClass() {
        super.ltiPath(LTI_BASE_PATH);
        super.loginInitiationPath(LOGIN_INITIATION_PATH);
        super.loginPath(LOGIN_PATH);
        super.useState(true);
    }

    protected OAuth2JWKSService lti13KeyPairService(HttpSecurity http) {
        return http.getSharedObject(ApplicationContext.class).getBean(OAuth2JWKSService.class);
    }

    protected Lti13Service lti13Service(HttpSecurity http) {
        return http.getSharedObject(ApplicationContext.class).getBean(Lti13Service.class);
    }

    protected ClientRegistrationRepository clientRegistrationRepository(HttpSecurity http) {
        return http.getSharedObject(ApplicationContext.class).getBean(ClientRegistrationRepository.class);
    }
}
