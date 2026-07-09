package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jspecify.annotations.Nullable;

final class InMemoryProviderFailureCooldown implements ProviderFailureCooldown {

    private static final ConcurrentMap<String, Instant> OPEN_COOLDOWNS = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public Instant cooldownUntil(String key) {
        Instant cooldownUntil = OPEN_COOLDOWNS.get(key);
        if (cooldownUntil != null && !cooldownUntil.isAfter(Instant.now())) {
            OPEN_COOLDOWNS.remove(key, cooldownUntil);
            return null;
        }
        return cooldownUntil;
    }

    @Override
    public void startCooldown(String key, Instant until) {
        OPEN_COOLDOWNS.merge(key, until, (existing, next) -> existing.isAfter(next) ? existing : next);
    }

    static void clearForTests() {
        OPEN_COOLDOWNS.clear();
    }
}
