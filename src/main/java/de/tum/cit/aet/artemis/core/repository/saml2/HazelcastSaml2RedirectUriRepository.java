package de.tum.cit.aet.artemis.core.repository.saml2;

import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.config.Saml2Enabled;

/**
 * Hazelcast-backed store for SAML2 redirect URI nonces.
 * <p>
 * Stores validated redirect_uri values keyed by UUID nonce during the SAML2 authentication flow.
 * Nonces are one-time use (atomically consumed on lookup) and expire after 5 minutes via a
 * per-entry Hazelcast TTL set on {@code put}.
 * <p>
 * This distributed store ensures the feature works in clustered Artemis deployments where
 * the SAML2 AuthnRequest and Response may be handled by different nodes.
 */
@Conditional(Saml2Enabled.class)
@Lazy
@Repository
public class HazelcastSaml2RedirectUriRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastSaml2RedirectUriRepository.class);

    private static final String MAP_NAME = "saml2-redirect-uri-nonce-map";

    private static final int NONCE_TTL_SECONDS = 300; // 5 minutes

    private final HazelcastInstance hazelcastInstance;

    private IMap<String, String> nonceMap;

    public HazelcastSaml2RedirectUriRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Resolves the distributed nonce map. Hazelcast access must not happen in the constructor
     * (see {@code ArchitectureTest.testNoHazelcastUsageInConstructors}), hence the {@code @PostConstruct}.
     */
    @PostConstruct
    public void init() {
        this.nonceMap = hazelcastInstance.getMap(MAP_NAME);
    }

    /**
     * Stores a nonce to redirect_uri mapping with a per-entry TTL, so abandoned SAML2 flows
     * expire automatically without an explicit cleanup.
     *
     * @param nonce       the UUID nonce (used as RelayState)
     * @param redirectUri the validated redirect URI
     */
    public void save(String nonce, String redirectUri) {
        nonceMap.put(nonce, redirectUri, NONCE_TTL_SECONDS, TimeUnit.SECONDS);
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
        String redirectUri = nonceMap.remove(nonce);
        if (redirectUri != null) {
            log.debug("Consumed SAML2 redirect nonce: {}", nonce);
        }
        return redirectUri;
    }
}
