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
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;
import org.springframework.stereotype.Repository;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.dto.passkey.PublicKeyCredentialCreationOptionsDTO;

/**
 * <p>
 * To ensure synchronization of WebAuthn credential creation options across multiple nodes, Hazelcast is utilized.
 * </p>
 * <p>
 * Credential creation options are short-lived, as they are only used during the registration process (e.g., when creating a new passkey).<br>
 * These options are removed from the shared storage once the registration process is completed or after a predefined time to live.
 * </p>
 */
@Profile(PROFILE_CORE)
@Repository
public class HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository implements PublicKeyCredentialCreationOptionsRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository.class);

    static final String DEFAULT_ATTR_NAME = HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository.class.getName().concat("ATTR_NAME");

    private final String attrName = DEFAULT_ATTR_NAME;

    private final HazelcastInstance hazelcastInstance;

    private static final String MAP_NAME = "http-session-public-key-credential-creation-options-map";

    private IMap<String, PublicKeyCredentialCreationOptionsDTO> creationOptionsMap;

    public HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @PostConstruct
    public void init() {
        int registrationOptionsTimeToLive = 300; // 5 minutes

        var mapConfig = hazelcastInstance.getConfig().getMapConfig(MAP_NAME);
        mapConfig.setTimeToLiveSeconds(registrationOptionsTimeToLive);
        creationOptionsMap = hazelcastInstance.getMap(MAP_NAME);
    }

    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialCreationOptions options) {

        HttpSession session = request.getSession();
        session.setAttribute(this.attrName, options);

        // the sessionId appears to change and does not equal the requestedSessionId, therefore, we use the userId instead
        var userId = request.getRemoteUser();
        if (userId == null) {
            log.warn("User ID is null, could not save PublicKeyCredentialCreationOptions");
            return;
        }

        if (options != null) {
            creationOptionsMap.put(userId, PublicKeyCredentialCreationOptionsDTO.publicKeyCredentialCreationOptionsToDTO(options));
        }
        else {
            creationOptionsMap.remove(session.getId());
        }
    }

    public PublicKeyCredentialCreationOptions load(HttpServletRequest request) {
        var userId = request.getRemoteUser();
        if (userId == null) {
            log.warn("User ID is null. This might indicate that the session does not exist or has expired. Unable to load PublicKeyCredentialCreationOptions.");
            return null;
        }

        return creationOptionsMap.get(userId).toPublicKeyCredentialCreationOptions();
    }
}
