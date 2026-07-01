package de.tum.cit.aet.artemis.core.repository.externallogin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.security.externallogin.ExternalLoginCodeData;

/**
 * Hazelcast-backed one-time-code store for the external-client browser login flow.
 * <p>
 * Codes are single-use (atomically consumed on lookup) and expire after a short per-entry TTL.
 * Distributed so the issue and exchange requests can hit different nodes in a cluster.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public class HazelcastExternalLoginCodeRepository {

    private static final String MAP_NAME = "external-login-code-map";

    private static final int CODE_TTL_SECONDS = 60;

    private final HazelcastInstance hazelcastInstance;

    private IMap<String, ExternalLoginCodeData> codeMap;

    public HazelcastExternalLoginCodeRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Resolves the distributed code map. Hazelcast access must not happen in the constructor
     * (see {@code ArchitectureTest.testNoHazelcastUsageInConstructors}), hence the {@code @PostConstruct}.
     */
    @PostConstruct
    public void init() {
        this.codeMap = hazelcastInstance.getMap(MAP_NAME);
    }

    /**
     * Stores the code data with a short per-entry TTL so abandoned flows expire automatically.
     *
     * @param code the one-time code
     * @param data the data bound to the code
     */
    public void save(String code, ExternalLoginCodeData data) {
        codeMap.put(code, data, CODE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Atomically retrieves and removes the data for the given code (single-use).
     *
     * @param code the one-time code
     * @return the data, or {@code null} if unknown, expired, or already consumed
     */
    @Nullable
    public ExternalLoginCodeData consume(String code) {
        return codeMap.remove(code);
    }
}
