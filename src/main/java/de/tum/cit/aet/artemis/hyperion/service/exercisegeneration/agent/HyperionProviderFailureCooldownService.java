package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;

/** Distributed hard-failure cooldown so one core node's quota/auth failure protects all Hyperion generation workers. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionProviderFailureCooldownService implements ProviderFailureCooldown {

    private static final String COOLDOWN_MAP_NAME = "hyperion-provider-failure-cooldowns";

    private final DistributedDataProvider distributedDataProvider;

    private DistributedMap<String, CooldownState> cooldownMap;

    public HyperionProviderFailureCooldownService(DistributedDataProvider distributedDataProvider) {
        this.distributedDataProvider = distributedDataProvider;
    }

    @PostConstruct
    public void init() {
        cooldownMap = distributedDataProvider.getExpiringMap(COOLDOWN_MAP_NAME, Duration.ofDays(1));
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
            long ttlMillis = Math.max(1L, Duration.between(Instant.now(), effectiveUntil).toMillis());
            cooldownMap.put(key, new CooldownState(effectiveUntil), Duration.ofMillis(ttlMillis));
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
