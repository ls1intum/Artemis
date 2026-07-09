package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/** Hazelcast-backed hard-failure cooldown so one core node's quota/auth failure protects all Hyperion generation workers. */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class HyperionProviderFailureCooldownService implements ProviderFailureCooldown {

    private static final String COOLDOWN_MAP_NAME = "hyperion-provider-failure-cooldowns";

    private final HazelcastInstance hazelcastInstance;

    private IMap<String, CooldownState> cooldownMap;

    public HyperionProviderFailureCooldownService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @PostConstruct
    public void init() {
        cooldownMap = hazelcastInstance.getMap(COOLDOWN_MAP_NAME);
    }

    @Nullable
    @Override
    public Instant cooldownUntil(String key) {
        CooldownState state = cooldownMap.get(key);
        if (state == null) {
            return null;
        }
        if (!state.cooldownUntil().isAfter(Instant.now())) {
            cooldownMap.remove(key, state);
            return null;
        }
        return state.cooldownUntil();
    }

    @Override
    public void startCooldown(String key, Instant until) {
        cooldownMap.lock(key);
        try {
            CooldownState state = cooldownMap.get(key);
            Instant effectiveUntil = state != null && state.cooldownUntil().isAfter(until) ? state.cooldownUntil() : until;
            long ttlSeconds = Math.max(1L, Duration.between(Instant.now(), effectiveUntil).toSeconds());
            cooldownMap.set(key, new CooldownState(effectiveUntil), ttlSeconds, TimeUnit.SECONDS);
        }
        finally {
            cooldownMap.unlock(key);
        }
    }

    private record CooldownState(Instant cooldownUntil) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
