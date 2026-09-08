package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jspecify.annotations.Nullable;

final class TestProviderFailureCooldown implements ProviderFailureCooldown {

    private final ConcurrentMap<String, Instant> cooldowns = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public Instant cooldownUntil(String key) {
        return cooldowns.get(key);
    }

    @Override
    public void startCooldown(String key, Instant until) {
        cooldowns.merge(key, until, (current, replacement) -> current.isAfter(replacement) ? current : replacement);
    }

}
