package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** Shared guard that lets Hyperion fail fast while a provider outage/quota/auth failure is cooling down. */
public interface ProviderFailureCooldown {

    @Nullable
    Instant cooldownUntil(String key);

    void startCooldown(String key, Instant until);

}
