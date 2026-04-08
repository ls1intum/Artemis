package de.tum.cit.aet.artemis.core.repository.passkey;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.WEBAUTHN_CHALLENGE_COOKIE_NAME;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.WebUtils;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * A distributed implementation of {@link PublicKeyCredentialRequestOptionsRepository} using Hazelcast
 * to store and synchronize WebAuthn authentication request options across multiple nodes.
 *
 * <p>
 * Instead of relying on HTTP sessions (which are not available under {@code SessionCreationPolicy.STATELESS}),
 * this implementation uses a random token stored in a cookie ({@value WEBAUTHN_CHALLENGE_COOKIE_NAME}) as the
 * key for looking up challenge options in a distributed Hazelcast map. This ensures that the challenge
 * correlation between the options request ({@code POST /webauthn/authenticate/options}) and the
 * authentication request ({@code POST /login/webauthn}) works reliably across multiple nodes,
 * and also supports conditional mediation (passkey autofill) where the pending credential request
 * may remain open for an extended period.
 * </p>
 *
 * <p>
 * The repository stores options in Hazelcast with a time-to-live of 5 minutes to accommodate
 * conditional mediation scenarios where users may not interact immediately.
 * </p>
 *
 * <p>
 * This bean is only active under the {@code core} Spring profile.
 * </p>
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public class HazelcastPublicKeyCredentialRequestOptionsRepository implements PublicKeyCredentialRequestOptionsRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastPublicKeyCredentialRequestOptionsRepository.class);

    private static final String DEVELOPMENT_PROFILE = "dev";

    /** Hazelcast map name for storing credential request options */
    private static final String MAP_NAME = "public-key-credentials-request-options-map";

    /** Time-to-live in seconds: 5 minutes to support conditional mediation (passkey autofill) */
    private static final int AUTH_OPTIONS_TTL_SECONDS = 300;

    private final HazelcastInstance hazelcastInstance;

    private final Environment environment;

    private IMap<String, PublicKeyCredentialRequestOptions> authOptionsMap;

    public HazelcastPublicKeyCredentialRequestOptionsRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, Environment environment) {
        this.hazelcastInstance = hazelcastInstance;
        this.environment = environment;
    }

    /**
     * Initializes the Hazelcast map configuration after dependency injection.
     * EventListener cannot be used here, as the bean is lazy.
     *
     * @see <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void init() {
        MapConfig mapConfig = hazelcastInstance.getConfig().getMapConfig(MAP_NAME);
        mapConfig.setTimeToLiveSeconds(AUTH_OPTIONS_TTL_SECONDS);
        authOptionsMap = hazelcastInstance.getMap(MAP_NAME);
    }

    /**
     * Saves the given {@link PublicKeyCredentialRequestOptions} in the Hazelcast distributed map
     * and sets a cookie with the lookup token.
     *
     * <p>
     * When {@code options} is not {@code null}, a new random token is generated, stored as the
     * Hazelcast map key, and set as a cookie on the response. When {@code options} is {@code null}
     * (cleanup after authentication), the existing token is read from the cookie, the Hazelcast
     * entry is removed, and the cookie is deleted.
     * </p>
     *
     * @param request  the current HTTP request
     * @param response the current HTTP response (used to set the challenge token cookie)
     * @param options  the WebAuthn challenge options to store, or {@code null} to remove
     */
    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialRequestOptions options) {
        if (options != null) {
            String token = UUID.randomUUID().toString();
            authOptionsMap.put(token, options);
            response.addHeader(HttpHeaders.SET_COOKIE, buildChallengeCookie(token, AUTH_OPTIONS_TTL_SECONDS).toString());
        }
        else {
            Cookie existingCookie = WebUtils.getCookie(request, WEBAUTHN_CHALLENGE_COOKIE_NAME);
            if (existingCookie != null) {
                authOptionsMap.remove(existingCookie.getValue());
            }
            response.addHeader(HttpHeaders.SET_COOKIE, buildChallengeCookie("", 0).toString());
        }
    }

    /**
     * Loads the previously saved {@link PublicKeyCredentialRequestOptions} from the Hazelcast map
     * using the token from the {@value WEBAUTHN_CHALLENGE_COOKIE_NAME} cookie.
     *
     * @param request the HTTP request (used to extract the challenge token cookie)
     * @return the stored {@link PublicKeyCredentialRequestOptions}, or {@code null} if the cookie
     *         is missing or no matching entry exists in Hazelcast
     */
    @Override
    public PublicKeyCredentialRequestOptions load(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, WEBAUTHN_CHALLENGE_COOKIE_NAME);
        if (cookie == null || cookie.getValue().isBlank()) {
            log.warn("No {} cookie found. The cookie may have expired or was not set. Unable to load PublicKeyCredentialRequestOptions.", WEBAUTHN_CHALLENGE_COOKIE_NAME);
            return null;
        }

        return authOptionsMap.get(cookie.getValue());
    }

    private ResponseCookie buildChallengeCookie(String value, int maxAgeSeconds) {
        Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isSecure = !activeProfiles.contains(DEVELOPMENT_PROFILE);

        return ResponseCookie.from(WEBAUTHN_CHALLENGE_COOKIE_NAME, value).httpOnly(true).sameSite("Lax").secure(isSecure).path("/").maxAge(maxAgeSeconds).build();
    }
}
