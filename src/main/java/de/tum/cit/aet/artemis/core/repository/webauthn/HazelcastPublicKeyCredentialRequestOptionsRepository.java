package de.tum.cit.aet.artemis.core.repository.webauthn;

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
 * <p>
 * To ensure synchronization of WebAuthn authentication request options across multiple nodes, Hazelcast is utilized.
 * </p>
 * <p>
 * Authentication options are short-lived, as the only user interaction in between is authentication (e.g., via Face ID).<br>
 * These options are removed from the shared storage once the authentication process is completed or after a predefined time to live.
 * </p>
 */
@Profile(PROFILE_CORE)
@Repository
public class HazelcastPublicKeyCredentialRequestOptionsRepository implements PublicKeyCredentialRequestOptionsRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastPublicKeyCredentialRequestOptionsRepository.class);

    private static final String MAP_NAME = "public-key-credentials-request-options-map";

    private IMap<String, PublicKeyCredentialRequestOptions> authOptionsMap;

    static final String DEFAULT_ATTR_NAME = PublicKeyCredentialRequestOptionsRepository.class.getName().concat(".ATTR_NAME");

    private final String attrName = DEFAULT_ATTR_NAME;

    private final HazelcastInstance hazelcastInstance;

    @PostConstruct
    public void init() {
        int AUTH_OPTIONS_TIME_TO_LIVE_IN_SECONDS = 120; // 2 minutes

        MapConfig mapConfig = hazelcastInstance.getConfig().getMapConfig(MAP_NAME);
        mapConfig.setTimeToLiveSeconds(AUTH_OPTIONS_TIME_TO_LIVE_IN_SECONDS);
        authOptionsMap = hazelcastInstance.getMap(MAP_NAME);
    }

    public HazelcastPublicKeyCredentialRequestOptionsRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

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

    @Override
    public PublicKeyCredentialRequestOptions load(HttpServletRequest request) {
        String sessionId = request.getRequestedSessionId();
        if (sessionId == null) {
            log.warn("Session ID is null. This might indicate that the session does not exist or has expired. Unable to load PublicKeyCredentialRequestOptions.");
            return null;
        }

        return authOptionsMap.get(sessionId);
    }
}
