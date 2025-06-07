package de.tum.cit.aet.artemis.core.repository.passkey;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

        if (session.getId() != null) {
            authOptionsMap.put(session.getId(), options);
        }
        // updateByRequestId(request, options);
    }

    /**
     * Loads the previously saved {@link PublicKeyCredentialRequestOptions} from the Hazelcast map
     * using the requested session ID from the HTTP request.
     *
     * @param request the HTTP request (used to extract session ID)
     * @return the stored {@link PublicKeyCredentialRequestOptions}, or {@code null} if not found or session is missing
     */
    @Override
    public PublicKeyCredentialRequestOptions load(HttpServletRequest request) {
        return getOptionsBySessionId(request);
    }

    private void updateByRequestId(HttpServletRequest request, PublicKeyCredentialRequestOptions options) {
        String sessionId = request.getSession().getId();
        String requestedSessionId = request.getRequestedSessionId();

        if (options != null) {
            if (sessionId != null) {
                authOptionsMap.put(sessionId, options);
            }
            if (requestedSessionId != null) {
                authOptionsMap.put(requestedSessionId, options);
            }
        }
        else {
            if (sessionId != null) {
                authOptionsMap.remove(sessionId);
            }
            if (requestedSessionId != null) {
                authOptionsMap.remove(requestedSessionId);
            }
        }
    }

    private PublicKeyCredentialRequestOptions getOptionsBySessionId(HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        String requestedSessionId = request.getRequestedSessionId();

        PublicKeyCredentialRequestOptions options = null;
        if (requestedSessionId != null) {
            options = authOptionsMap.get(requestedSessionId);
        }

        if (sessionId != null && options == null) {
            // we don't have options from the requested session id, so we try to get the options from the current session id
            // we also do not need to update anything in hazelcast in this case
            return authOptionsMap.get(sessionId);
        }

        // we found options with the old id (otherwise we would have returned null above), so we need to update the hazelcast map
        boolean hasSessionIdChanged = sessionId != null;
        if (hasSessionIdChanged) {
            // authOptionsMap.remove(requestedSessionId);
            authOptionsMap.put(sessionId, options);
        }

        return options;
    }
}
