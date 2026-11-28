package de.tum.cit.aet.artemis.lti.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.jwt.JWTFilter;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.repository.LtiPlatformConfigurationRepository;
import de.tum.cit.aet.artemis.lti.service.Lti13Service;
import de.tum.cit.aet.artemis.lti.service.OnlineCourseConfigurationService;
import uk.ac.ox.ctl.lti13.Lti13Configurer;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.authentication.OidcLaunchFlowAuthenticationProvider;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.HttpSessionOAuth2AuthorizationRequestRepository;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.Lti13InitiatingLoginRequestResolver;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OAuth2AuthorizationRequestRedirectFilter;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OAuth2LoginAuthenticationFilter;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OptimisticAuthorizationRequestRepository;

/**
 * Configures and registers Security Filters to handle LTI 1.3 Resource Link Launches
 */
@Conditional(LtiEnabled.class)
@Component
@Lazy
public class CustomLti13Configurer extends Lti13Configurer {

    /** Path for login. **/
    private static final String LOGIN_PATH = "/auth-login";

    /** Path for initiating login process. */
    private static final String LOGIN_INITIATION_PATH = "/initiate-login";

    /** Base for LTI 1.3 API endpoints. */
    public static final String LTI13_BASE = "api/lti/public/lti13";

    /** Base path for LTI 1.3 API endpoints. */
    public static final String LTI13_BASE_PATH = "/" + LTI13_BASE;

    /** Full path for LTI 1.3 login. */
    public static final String LTI13_LOGIN_PATH = LTI13_BASE_PATH + LOGIN_PATH;

    /** Full path for LTI 1.3 login initiation. */
    public static final String LTI13_LOGIN_INITIATION_PATH = LTI13_BASE_PATH + LOGIN_INITIATION_PATH;

    /** Redirect proxy path for LTI 1.3 login. */
    public static final String LTI13_LOGIN_REDIRECT_PROXY_PATH = LTI13_BASE_PATH + "/auth-callback";

    // without leading '/'
    public static final String LTI13_LOGIN_REDIRECT_PROXY = LTI13_BASE + "/auth-callback";

    /** Path for LTI 1.3 deep linking. */
    public static final String LTI13_DEEPLINK_REDIRECT_PATH = LTI13_BASE_PATH + "/deep-link";

    // without leading '/'
    public static final String LTI13_DEEPLINK_REDIRECT = LTI13_BASE + "/deep-link";

    /** Path for LTI 1.3 deep linking redirect. */
    public static final String LTI13_DEEPLINK_SELECT_COURSE_PATH = "/lti/select-course";

    /** Value for LTI 1.3 deep linking request message. */
    public static final String LTI13_DEEPLINK_MESSAGE_REQUEST = "LtiDeepLinkingRequest";

    private static final Logger log = LoggerFactory.getLogger(CustomLti13Configurer.class);

    /** How long the origins of the configured platforms are reused before they are read again. */
    private static final Duration PLATFORM_ORIGINS_CACHE_DURATION = Duration.ofMinutes(1);

    private final DistributedStateAuthorizationRequestRepository stateRepository;

    private final LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    private final AtomicReference<PlatformOrigins> cachedPlatformOrigins = new AtomicReference<>(new PlatformOrigins(Set.of(), false));

    private volatile long cachedPlatformOriginsAtNanos;

    private volatile boolean platformOriginsCached;

    /**
     * The origins of the configured LTI platforms, as they were read last.
     *
     * @param origins the origins, empty while no platform is configured
     * @param read    whether a read of the platforms has succeeded. While it has not, the empty origins mean that the platforms are unknown, not that there are none.
     */
    public record PlatformOrigins(Set<String> origins, boolean read) {

        /** The state of an installation without LTI platforms: nothing can embed the client, and nothing is unknown. */
        public static final PlatformOrigins NONE = new PlatformOrigins(Set.of(), true);
    }

    private final ReentrantLock refreshLock = new ReentrantLock();

    private final AtomicReference<Set<String>> platformsWarnedAbout = new AtomicReference<>(Set.of());

    public CustomLti13Configurer(DistributedStateAuthorizationRequestRepository stateRepository, LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository) {
        super.ltiPath(LTI13_BASE_PATH);
        super.loginInitiationPath(LOGIN_INITIATION_PATH);
        super.loginPath(LOGIN_PATH);
        this.stateRepository = stateRepository;
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
    }

    /**
     * The origins ({@code scheme://host[:port]}) of the configured LTI platforms, which are the origins that embed the client when they launch it. They come from the
     * issuer and the authorization address of each platform. The result is cached for a minute, because it is needed for every response.
     * <p>
     * Only one request refreshes the cache at a time. The other requests that arrive meanwhile get the cached origins instead of querying the database as well. A failed
     * refresh counts as a refresh: the previous origins stay in place and the next attempt waits for the cache duration, so that an outage of the database does not make
     * every request query it. The very first read has no previous origins to serve, so concurrent requests wait for it. If that read fails, the result says that the
     * platforms are not known yet, which is not the same as no platform being configured.
     *
     * @return the origins, which are empty while no platform is configured, and whether a read of the platforms has succeeded yet
     */
    public PlatformOrigins getPlatformOrigins() {
        if (isCacheFresh()) {
            return cachedPlatformOrigins.get();
        }
        if (platformOriginsCached) {
            if (!refreshLock.tryLock()) {
                return cachedPlatformOrigins.get();
            }
        }
        else {
            refreshLock.lock();
        }
        try {
            if (isCacheFresh()) {
                return cachedPlatformOrigins.get();
            }
            refreshPlatformOrigins();
            return cachedPlatformOrigins.get();
        }
        finally {
            refreshLock.unlock();
        }
    }

    private boolean isCacheFresh() {
        return platformOriginsCached && System.nanoTime() - cachedPlatformOriginsAtNanos < PLATFORM_ORIGINS_CACHE_DURATION.toNanos();
    }

    private void refreshPlatformOrigins() {
        try {
            Set<String> origins = new LinkedHashSet<>();
            Set<String> platformsWithoutOrigin = new TreeSet<>();
            for (LtiPlatformConfiguration platform : ltiPlatformConfigurationRepository.findAll()) {
                String issuerOrigin = originOf(platform.getOriginalUrl());
                String authorizationOrigin = originOf(platform.getAuthorizationUri());
                if (issuerOrigin == null && authorizationOrigin == null) {
                    platformsWithoutOrigin.add(String.valueOf(platform.getRegistrationId()));
                }
                if (issuerOrigin != null) {
                    origins.add(issuerOrigin);
                }
                if (authorizationOrigin != null) {
                    origins.add(authorizationOrigin);
                }
            }
            warnAboutNewPlatformsWithoutOrigin(platformsWithoutOrigin);
            cachedPlatformOrigins.set(new PlatformOrigins(Collections.unmodifiableSortedSet(new TreeSet<>(origins)), true));
        }
        catch (RuntimeException e) {
            // The previous origins stay in place rather than opening or closing the framing because of a failed read. Before the first successful read, they are marked as unknown.
            log.warn("The LTI platforms could not be read to derive the origins that may embed the client: {}", e.getMessage());
        }
        cachedPlatformOriginsAtNanos = System.nanoTime();
        platformOriginsCached = true;
    }

    /**
     * Warns once for every platform whose embedding origin cannot be derived, and again only if the platform is fixed and breaks later. A warning per refresh would
     * repeat every minute.
     */
    private void warnAboutNewPlatformsWithoutOrigin(Set<String> platformsWithoutOrigin) {
        Set<String> alreadyWarnedAbout = platformsWarnedAbout.get();
        for (String registrationId : platformsWithoutOrigin) {
            if (!alreadyWarnedAbout.contains(registrationId)) {
                log.warn("No origin that may embed the client can be derived from the issuer or the authorization address of the LTI platform {}. "
                        + "List the origin of its learning management system in artemis.security.frame-ancestors.", registrationId);
            }
        }
        platformsWarnedAbout.set(Set.copyOf(platformsWithoutOrigin));
    }

    @Nullable
    private static String originOf(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (uri.getHost() == null || !("https".equals(scheme) || "http".equals(scheme))) {
                return null;
            }
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        }
        catch (URISyntaxException _) {
            log.debug("The address {} of an LTI platform is not a valid URI", url);
            return null;
        }
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
        http.addFilterAfter(new Lti13LaunchFilter(defaultLoginFilter, LTI13_LOGIN_PATH, lti13Service(http)), JWTFilter.class);
    }

    /**
     * Override the library's initiation filter configuration to use Spring 7-compatible replacements
     * for two upstream classes that depend on APIs removed in Spring Framework 7 / Spring Security 7:
     * <ul>
     * <li>{@link Lti13PathRegistrationResolver} replaces the library's {@code PathOIDCInitiationRegistrationResolver},
     * which used the removed {@code AntPathRequestMatcher}.</li>
     * <li>{@link Lti13InitiatingLoginRequestResolver} replaces the library's
     * {@code OIDCInitiatingLoginRequestResolver}, which called the removed
     * {@code UriComponentsBuilder.fromHttpUrl(String)} and crashed Step 1 of the LTI 1.3 login flow
     * with {@link NoSuchMethodError} (issue #12739).</li>
     * </ul>
     * Both replacements can be deleted and the upstream classes restored once
     * {@code uk.ac.ox.ctl:spring-security-lti13} releases a Spring 7-compatible version
     * (tracked upstream in <a href="https://github.com/oxctl/spring-security-lti13/pull/60">oxctl/spring-security-lti13#60</a>).
     */
    @Override
    protected OAuth2AuthorizationRequestRedirectFilter configureInitiationFilter(ClientRegistrationRepository clientRegistrationRepository,
            OptimisticAuthorizationRequestRepository authorizationRequestRepository) {
        var registrationResolver = new Lti13PathRegistrationResolver(ltiPath + loginInitiationPath);
        var resolver = new Lti13InitiatingLoginRequestResolver(clientRegistrationRepository, registrationResolver);
        var filter = new OAuth2AuthorizationRequestRedirectFilter(resolver);
        filter.setAuthorizationRequestRepository(authorizationRequestRepository);
        return filter;
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
