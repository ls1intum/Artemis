package de.tum.cit.aet.artemis.core.repository.saml2;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SAML2;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * Hazelcast-backed store for SAML2 redirect URI nonces.
 * <p>
 * Stores validated redirect_uri values keyed by UUID nonce during the SAML2 authentication flow.
 * Nonces are one-time use (atomically consumed on lookup) and expire after 5 minutes via Hazelcast TTL.
 * <p>
 * This distributed store ensures the feature works in clustered Artemis deployments where
 * the SAML2 AuthnRequest and Response may be handled by different nodes.
 */
@Profile(PROFILE_SAML2)
@Lazy
@Repository
public class HazelcastSaml2RedirectUriRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastSaml2RedirectUriRepository.class);

    private static final String MAP_NAME = "saml2-redirect-uri-nonce-map";

    private static final int NONCE_TTL_SECONDS = 300; // 5 minutes

    private final HazelcastInstance hazelcastInstance;

    @Nullable
    private IMap<String, String> nonceMap;

    public HazelcastSaml2RedirectUriRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @PostConstruct
    public void init() {
        MapConfig mapConfig = hazelcastInstance.getConfig().getMapConfig(MAP_NAME);
        mapConfig.setTimeToLiveSeconds(NONCE_TTL_SECONDS);
    }

    private IMap<String, String> getNonceMap() {
        if (this.nonceMap == null) {
            this.nonceMap = hazelcastInstance.getMap(MAP_NAME);
        }
        return this.nonceMap;
    }

    /**
     * Stores a nonce to redirect_uri mapping.
     *
     * @param nonce       the UUID nonce (used as RelayState)
     * @param redirectUri the validated redirect URI
     */
    public void save(String nonce, String redirectUri) {
        getNonceMap().put(nonce, redirectUri);
        log.debug("Saved SAML2 redirect nonce: {}", nonce);
    }

    /**
     * Atomically retrieves and removes the redirect_uri for the given nonce.
     * Returns null if the nonce does not exist or has expired.
     *
     * @param nonce the UUID nonce from RelayState
     * @return the redirect_uri, or null if not found/expired/already consumed
     */
    @Nullable
    public String consumeAndRemove(String nonce) {
        String redirectUri = getNonceMap().remove(nonce);
        if (redirectUri != null) {
            log.debug("Consumed SAML2 redirect nonce: {}", nonce);
        }
        return redirectUri;
    }
}
