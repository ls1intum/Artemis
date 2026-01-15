package de.tum.cit.aet.artemis.core.repository.passkey;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;
import org.springframework.stereotype.Repository;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * A distributed implementation of {@link PublicKeyCredentialRequestOptionsRepository} using Hazelcast
 * to store and synchronize WebAuthn authentication request options across multiple nodes.
 *
 * <p>
 * This implementation ensures that authentication challenges (e.g., Face ID, fingerprint scan)
 * remain consistent in clustered environments, supporting stateless or load-balanced deployments.
 * </p>
 *
 * <p>
 * The repository stores options in Hazelcast with a short time-to-live (2 minutes by default),
 * since authentication is a fast, single-step user interaction. Stored options are removed after use
 * or expiration.
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

    /** Hazelcast map name for storing credential request options */
    private static final String MAP_NAME = "public-key-credentials-request-options-map";

    /** Default session attribute name used to store options in the local session */
    static final String DEFAULT_ATTR_NAME = PublicKeyCredentialRequestOptionsRepository.class.getName().concat(".ATTR_NAME");

    /** Session attribute name used internally */
    private final String attrName = DEFAULT_ATTR_NAME;

    /** Hazelcast instance injected via constructor */
    private final HazelcastInstance hazelcastInstance;

    /** Reference to the Hazelcast distributed map */
    private IMap<String, PublicKeyCredentialRequestOptions> authOptionsMap;

    /**
     * Constructs the repository using the injected Hazelcast instance.
     *
     * @param hazelcastInstance the shared Hazelcast cluster instance
     */
    public HazelcastPublicKeyCredentialRequestOptionsRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Initializes the Hazelcast map configuration after dependency injection.
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     *
     * <p>
     * Sets the time-to-live for WebAuthn request options to 2 minutes.
     * </p>
     */
    @PostConstruct
    public void init() {
        int AUTH_OPTIONS_TIME_TO_LIVE_IN_SECONDS = 120; // 2 minutes

        MapConfig mapConfig = hazelcastInstance.getConfig().getMapConfig(MAP_NAME);
        mapConfig.setTimeToLiveSeconds(AUTH_OPTIONS_TIME_TO_LIVE_IN_SECONDS);
        authOptionsMap = hazelcastInstance.getMap(MAP_NAME);
    }

    /**
     * Saves the given {@link PublicKeyCredentialRequestOptions} in both the local HTTP session
     * and the Hazelcast distributed map.
     *
     * <p>
     * If {@code options} is {@code null}, the entry is removed instead.
     * </p>
     *
     * @param request  the current HTTP request (used to get the session)
     * @param response the current HTTP response (not used)
     * @param options  the WebAuthn challenge options to store or remove
     */
    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialRequestOptions options) {
        HttpSession session = request.getSession();
        session.setAttribute(this.attrName, options);

        if (options != null) {
            authOptionsMap.put(session.getId(), options);
        }
        else {
            authOptionsMap.remove(session.getId());
        }
    }

    /**
     * Loads the previously saved {@link PublicKeyCredentialRequestOptions} from the Hazelcast map
     * using the requested session ID from the HTTP request.
     *
     * <p>
     * Falls back to checking the HTTP session attribute if the requested session ID is null
     * (e.g., in test environments using MockMvc where no session cookie is sent).
     * </p>
     *
     * @param request the HTTP request (used to extract session ID)
     * @return the stored {@link PublicKeyCredentialRequestOptions}, or {@code null} if not found or session is missing
     */
    @Override
    public PublicKeyCredentialRequestOptions load(HttpServletRequest request) {
        String sessionId = request.getRequestedSessionId();
        if (sessionId == null) {
            // Fallback to HTTP session for test environments (e.g., MockMvc)
            // where no session cookie is sent but session attributes are available
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object options = session.getAttribute(this.attrName);
                if (options instanceof PublicKeyCredentialRequestOptions storedOptions) {
                    return storedOptions;
                }
            }
            log.warn("Session ID is null and no options found in HTTP session. Unable to load PublicKeyCredentialRequestOptions.");
            return null;
        }

        return authOptionsMap.get(sessionId);
    }
}
