package de.tum.in.www1.artemis.config.lti;

import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import de.tum.in.www1.artemis.security.lti.Lti13LaunchFilter;
import de.tum.in.www1.artemis.service.OnlineCourseConfigurationService;
import de.tum.in.www1.artemis.service.connectors.Lti13Service;
import uk.ac.ox.ctl.lti13.Lti13Configurer;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.authentication.OidcLaunchFlowAuthenticationProvider;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OAuth2LoginAuthenticationFilter;

/**
 * Configures and registers Security Filters to handle LTI 1.3 Resource Link Launches
 */
public class CustomLti13Configurer extends Lti13Configurer {

    private static final String LOGIN_PATH = "/auth-login";

    private static final String LOGIN_INITIATION_PATH = "/initiate-login";

    public static final String LTI13_BASE_PATH = "/api/lti13";

    public static final String LTI13_LOGIN_PATH = LTI13_BASE_PATH + LOGIN_PATH;

    public static final String LTI13_LOGIN_INITIATION_PATH = LTI13_BASE_PATH + LOGIN_INITIATION_PATH;

    public static final String LTI13_LOGIN_REDIRECT_PROXY_PATH = LTI13_BASE_PATH + "/auth-callback";

    public static final String JWKS_PATH = "/.well-known/jwks.json";

    public CustomLti13Configurer() {
        super.ltiPath(LTI13_BASE_PATH);
        super.loginInitiationPath(LOGIN_INITIATION_PATH);
        super.loginPath(LOGIN_PATH);
        super.useState(true);
    }

    @Override
    public void configure(HttpSecurity http) {
        AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository = configureRequestRepository();
        OidcLaunchFlowAuthenticationProvider oidcLaunchFlowAuthenticationProvider = configureAuthenticationProvider(http);

        // Step 1 of the IMS SEC
        // https://www.imsglobal.org/spec/security/v1p0/#step-1-third-party-initiated-login
        // login initiation is handled by spring-security-lti13 completely
        http.addFilterAfter(this.configureInitiationFilter(clientRegistrationRepository(http), authorizationRequestRepository), LogoutFilter.class);

        // Step 3 of the IMS SEC
        // https://www.imsglobal.org/spec/security/v1p0/#step-3-authentication-response
        OAuth2LoginAuthenticationFilter defaultLoginFilter = configureLoginFilter(clientRegistrationRepository(http), oidcLaunchFlowAuthenticationProvider,
                authorizationRequestRepository);
        http.addFilterAfter(new Lti13LaunchFilter(defaultLoginFilter, LTI13_BASE_PATH + LOGIN_PATH, lti13Service(http)), AbstractPreAuthenticatedProcessingFilter.class);
    }

    protected Lti13Service lti13Service(HttpSecurity http) {
        return http.getSharedObject(ApplicationContext.class).getBean(Lti13Service.class);
    }

    protected ClientRegistrationRepository clientRegistrationRepository(HttpSecurity http) {
        return http.getSharedObject(ApplicationContext.class).getBean(OnlineCourseConfigurationService.class);
    }
}
