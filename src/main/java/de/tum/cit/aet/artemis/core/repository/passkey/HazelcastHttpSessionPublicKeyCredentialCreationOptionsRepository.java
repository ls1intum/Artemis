package de.tum.cit.aet.artemis.core.repository.passkey;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.WEBAUTHN_REGISTRATION_CHALLENGE_COOKIE_NAME;

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
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.WebUtils;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.dto.passkey.PublicKeyCredentialCreationOptionsDTO;

/**
 * A distributed implementation of {@link PublicKeyCredentialCreationOptionsRepository} using Hazelcast
 * to store and synchronize WebAuthn credential creation options across nodes in a clustered deployment.
 *
 * <p>
 * Instead of relying on HTTP sessions, this implementation uses a random token stored in a cookie
 * ({@value WEBAUTHN_REGISTRATION_CHALLENGE_COOKIE_NAME}) as the key for looking up challenge options
 * in a distributed Hazelcast map. This is consistent with the authentication flow in
 * {@link HazelcastPublicKeyCredentialRequestOptionsRepository} and avoids session-related inconsistencies.
 * </p>
 *
 * <p>
 * Credential creation options are short-lived and stored with a time-to-live (TTL) of 5 minutes.
 * After registration is complete or the TTL expires, the entry is removed.
 * </p>
 *
 * <p>
 * Activated only under the {@code core} Spring profile.
 * </p>
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public class HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository implements PublicKeyCredentialCreationOptionsRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository.class);

    private static final String DEVELOPMENT_PROFILE = "dev";

    /** Name of the Hazelcast map used for credential creation options */
    private static final String MAP_NAME = "http-session-public-key-credential-creation-options-map";

    /** Time-to-live in seconds: 5 minutes */
    private static final int REGISTRATION_OPTIONS_TIME_TO_LIVE_SECONDS = 300;

    private final HazelcastInstance hazelcastInstance;

    private final Environment environment;

    private IMap<String, PublicKeyCredentialCreationOptionsDTO> creationOptionsMap;

    /**
     * Constructs the repository using the injected Hazelcast instance.
     *
     * @param hazelcastInstance the Hazelcast cluster instance
     * @param environment       the Spring environment, used to determine the active profile for cookie security settings
     */
    public HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, Environment environment) {
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
        mapConfig.setTimeToLiveSeconds(REGISTRATION_OPTIONS_TIME_TO_LIVE_SECONDS);
        creationOptionsMap = hazelcastInstance.getMap(MAP_NAME);
    }

    /**
     * Saves the given {@link PublicKeyCredentialCreationOptions} in the Hazelcast distributed map
     * and sets a cookie with the lookup token.
     *
     * <p>
     * When {@code options} is not {@code null}, a new random token is generated, stored as the
     * Hazelcast map key, and set as a cookie on the response. When {@code options} is {@code null}
     * (cleanup after registration), the existing token is read from the cookie, the Hazelcast
     * entry is removed, and the cookie is deleted.
     * </p>
     *
     * @param request  the current HTTP request
     * @param response the current HTTP response (used to set the challenge token cookie)
     * @param options  the credential creation options to store, or {@code null} to remove
     */
    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialCreationOptions options) {
        boolean storeNewChallenge = options != null;
        if (storeNewChallenge) {
            String token = UUID.randomUUID().toString();
            creationOptionsMap.put(token, PublicKeyCredentialCreationOptionsDTO.publicKeyCredentialCreationOptionsToDTO(options));
            response.addHeader(HttpHeaders.SET_COOKIE, buildChallengeCookie(token, REGISTRATION_OPTIONS_TIME_TO_LIVE_SECONDS).toString());
        }
        else {
            // clear old challenge
            Cookie existingCookie = WebUtils.getCookie(request, WEBAUTHN_REGISTRATION_CHALLENGE_COOKIE_NAME);
            if (existingCookie != null) {
                creationOptionsMap.remove(existingCookie.getValue());
            }
            response.addHeader(HttpHeaders.SET_COOKIE, buildChallengeCookie("", 0).toString());
        }
    }

    /**
     * Loads the previously saved {@link PublicKeyCredentialCreationOptions} from the Hazelcast map
     * using the token from the {@value WEBAUTHN_REGISTRATION_CHALLENGE_COOKIE_NAME} cookie.
     *
     * @param request the HTTP request (used to extract the challenge token cookie)
     * @return the stored {@link PublicKeyCredentialCreationOptions}, or {@code null} if the cookie
     *         is missing or no matching entry exists in Hazelcast
     */
    @Override
    public PublicKeyCredentialCreationOptions load(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, WEBAUTHN_REGISTRATION_CHALLENGE_COOKIE_NAME);
        if (cookie == null || cookie.getValue().isBlank()) {
            log.warn("No {} cookie found. The cookie may have expired or was not set. Unable to load PublicKeyCredentialCreationOptions.",
                    WEBAUTHN_REGISTRATION_CHALLENGE_COOKIE_NAME);
            return null;
        }

        PublicKeyCredentialCreationOptionsDTO creationOptions = creationOptionsMap.get(cookie.getValue());
        if (creationOptions == null) {
            log.warn("No cached PublicKeyCredentialCreationOptions found for token from cookie.");
            return null;
        }

        return creationOptions.toPublicKeyCredentialCreationOptions();
    }

    private ResponseCookie buildChallengeCookie(String value, int maxAgeSeconds) {
        Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isSecure = !activeProfiles.contains(DEVELOPMENT_PROFILE);

        return ResponseCookie.from(WEBAUTHN_REGISTRATION_CHALLENGE_COOKIE_NAME, value).httpOnly(true).sameSite("Lax").secure(isSecure).path("/").maxAge(maxAgeSeconds).build();
    }
}
