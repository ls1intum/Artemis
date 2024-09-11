package de.tum.cit.aet.artemis.lti.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.jwt.JWTFilter;
import de.tum.cit.aet.artemis.core.web.filter.Lti13LaunchFilter;
import de.tum.cit.aet.artemis.lti.service.Lti13Service;
import de.tum.cit.aet.artemis.service.OnlineCourseConfigurationService;
import uk.ac.ox.ctl.lti13.Lti13Configurer;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.authentication.OidcLaunchFlowAuthenticationProvider;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.HttpSessionOAuth2AuthorizationRequestRepository;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OAuth2LoginAuthenticationFilter;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OptimisticAuthorizationRequestRepository;

/**
 * Configures and registers Security Filters to handle LTI 1.3 Resource Link Launches
 */
@Profile("lti")
@Component
public class CustomLti13Configurer extends Lti13Configurer {

    /** Path for login. **/
    private static final String LOGIN_PATH = "/auth-login";

    /** Path for initiating login process. */
    private static final String LOGIN_INITIATION_PATH = "/initiate-login";

    /** Base path for LTI 1.3 API endpoints. */
    public static final String LTI13_BASE_PATH = "api/public/lti13";

    /** Full path for LTI 1.3 login. */
    public static final String LTI13_LOGIN_PATH = LTI13_BASE_PATH + LOGIN_PATH;

    /** Full path for LTI 1.3 login initiation. */
    public static final String LTI13_LOGIN_INITIATION_PATH = LTI13_BASE_PATH + LOGIN_INITIATION_PATH;

    /** Redirect proxy path for LTI 1.3 login. */
    public static final String LTI13_LOGIN_REDIRECT_PROXY_PATH = LTI13_BASE_PATH + "/auth-callback";

    /** Path for LTI 1.3 deep linking. */
    public static final String LTI13_DEEPLINK_REDIRECT_PATH = LTI13_BASE_PATH + "/deep-link";

    /** Path for LTI 1.3 deep linking redirect. */
    public static final String LTI13_DEEPLINK_SELECT_COURSE_PATH = "/lti/select-course";

    /** Value for LTI 1.3 deep linking request message. */
    public static final String LTI13_DEEPLINK_MESSAGE_REQUEST = "LtiDeepLinkingRequest";

    private final DistributedStateAuthorizationRequestRepository stateRepository;

    public CustomLti13Configurer(DistributedStateAuthorizationRequestRepository stateRepository) {
        super.ltiPath("/" + LTI13_BASE_PATH);
        super.loginInitiationPath(LOGIN_INITIATION_PATH);
        super.loginPath(LOGIN_PATH);
        this.stateRepository = stateRepository;
    }

    @Override
    public void configure(HttpSecurity http) {
        OptimisticAuthorizationRequestRepository authorizationRequestRepository = configureRequestRepository();
        OidcLaunchFlowAuthenticationProvider oidcLaunchFlowAuthenticationProvider = configureAuthenticationProvider(http);

        // Step 1 of the IMS SEC
        // https://www.imsglobal.org/spec/security/v1p0/#step-1-third-party-initiated-login
        // login initiation is handled by spring-security-lti13 completely
        http.addFilterAfter(this.configureInitiationFilter(clientRegistrationRepository(http), authorizationRequestRepository), LogoutFilter.class);

        // Step 3 of the IMS SEC
        // https://www.imsglobal.org/spec/security/v1p0/#step-3-authentication-response
        OAuth2LoginAuthenticationFilter defaultLoginFilter = configureLoginFilter(clientRegistrationRepository(http), oidcLaunchFlowAuthenticationProvider,
                authorizationRequestRepository);
        http.addFilterAfter(new Lti13LaunchFilter(defaultLoginFilter, "/" + LTI13_LOGIN_PATH, lti13Service(http)), JWTFilter.class);
    }

    protected Lti13Service lti13Service(HttpSecurity http) {
        return http.getSharedObject(ApplicationContext.class).getBean(Lti13Service.class);
    }

    protected ClientRegistrationRepository clientRegistrationRepository(HttpSecurity http) {
        return http.getSharedObject(ApplicationContext.class).getBean(OnlineCourseConfigurationService.class);
    }

    /**
     * Configures and returns an {@link StateBasedOptimisticAuthorizationRequestRepository} for handling OAuth2 authorization requests.
     * This method sets up a multinode-distributed state repository for managing authorization requests using Hazelcast.
     * This is necessary to support LTI on multinode systems where the different requests might get processed by different nodes.
     *
     * @return An instance of {@link StateBasedOptimisticAuthorizationRequestRepository} that combines session-based and distributed state management.
     */
    @Override
    protected OptimisticAuthorizationRequestRepository configureRequestRepository() {
        HttpSessionOAuth2AuthorizationRequestRepository sessionRepository = new HttpSessionOAuth2AuthorizationRequestRepository();
        stateRepository.setLimitIpAddress(limitIpAddresses);
        return new StateBasedOptimisticAuthorizationRequestRepository(sessionRepository, stateRepository);
    }
}
