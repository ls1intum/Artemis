package de.tum.cit.aet.artemis.core.repository.passkey;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;
import org.springframework.stereotype.Repository;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.dto.passkey.PublicKeyCredentialCreationOptionsDTO;

/**
 * A distributed implementation of {@link PublicKeyCredentialCreationOptionsRepository} using Hazelcast
 * to store and synchronize WebAuthn credential creation options across nodes in a clustered deployment.
 *
 * <p>
 * This is used during the WebAuthn registration (passkey creation) process. Credential options are short-lived
 * and stored with a time-to-live (TTL) of 5 minutes. After registration is complete or the TTL expires, the entry is removed.
 * </p>
 *
 * <p>
 * Instead of using session IDs, this implementation indexes options by the authenticated {@code userId} (from {@link HttpServletRequest#getRemoteUser()}),
 * to ensure consistency in environments where session IDs may change unexpectedly.
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

    /** Default attribute name for storing creation options in session (not used for loading) */
    static final String DEFAULT_ATTR_NAME = HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository.class.getName().concat("ATTR_NAME");

    private final String attrName = DEFAULT_ATTR_NAME;

    private final HazelcastInstance hazelcastInstance;

    /** Name of the Hazelcast map used for credential creation options */
    private static final String MAP_NAME = "http-session-public-key-credential-creation-options-map";

    @Nullable
    private IMap<String, PublicKeyCredentialCreationOptionsDTO> creationOptionsMap;

    /**
     * Constructs the repository using the injected Hazelcast instance.
     *
     * @param hazelcastInstance the Hazelcast cluster instance
     */
    public HazelcastHttpSessionPublicKeyCredentialCreationOptionsRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Initializes the Hazelcast map with a TTL of 5 minutes for credential creation options.
     */
    @PostConstruct
    public void init() {
        int registrationOptionsTimeToLive = 60 * 5; // 5 minutes

        MapConfig mapConfig = hazelcastInstance.getConfig().getMapConfig(MAP_NAME);
        mapConfig.setTimeToLiveSeconds(registrationOptionsTimeToLive);
    }

    /**
     * Lazy init: Retrieves the Hazelcast map that stores the public key credential creation options.
     * If the map is not initialized, it initializes it.
     *
     * @return The map of public key credential creation options.
     */
    private IMap<String, PublicKeyCredentialCreationOptionsDTO> getCreationOptionsMap() {
        if (this.creationOptionsMap == null) {
            this.creationOptionsMap = hazelcastInstance.getMap(MAP_NAME);
        }
        return this.creationOptionsMap;
    }

    /**
     * Saves the {@link PublicKeyCredentialCreationOptions} both in the HTTP session and the distributed Hazelcast map.
     *
     * <p>
     * The HTTP session is used locally, while the Hazelcast map ensures distributed availability.
     * The user ID (from {@code request.getRemoteUser()}) is used as the key instead of the session ID,
     * due to inconsistencies in session ID handling during WebAuthn flows.
     * </p>
     *
     * @param request  the HTTP request, used to get the session and remote user
     * @param response the HTTP response (not used)
     * @param options  the credential creation options to store; if {@code null}, the entry is removed
     */
    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialCreationOptions options) {

        HttpSession session = request.getSession();
        session.setAttribute(this.attrName, options);

        // the sessionId appears to change and does not equal the requestedSessionId, therefore, we use the userId instead
        String userId = request.getRemoteUser();
        if (userId == null) {
            log.warn("User ID is null, could not save PublicKeyCredentialCreationOptions");
            return;
        }

        if (options != null) {
            getCreationOptionsMap().put(userId, PublicKeyCredentialCreationOptionsDTO.publicKeyCredentialCreationOptionsToDTO(options));
        }
        else {
            getCreationOptionsMap().remove(session.getId());
        }
    }

    /**
     * Loads the previously saved {@link PublicKeyCredentialCreationOptions} from the Hazelcast map
     * using the authenticated user ID.
     *
     * @param request the HTTP request, used to extract the user ID
     * @return the restored credential creation options, or {@code null} if not found or user not authenticated
     */
    public PublicKeyCredentialCreationOptions load(HttpServletRequest request) {
        String userId = request.getRemoteUser();
        if (userId == null) {
            log.warn("User ID is null. This might indicate that the session does not exist or has expired. Unable to load PublicKeyCredentialCreationOptions.");
            return null;
        }

        PublicKeyCredentialCreationOptionsDTO creationOptions = getCreationOptionsMap().get(userId);
        if (creationOptions == null) {
            log.warn("No cached PublicKeyCredentialCreationOptions found for user '{}'", userId);
            return null;
        }

        return creationOptions.toPublicKeyCredentialCreationOptions();
    }
}
