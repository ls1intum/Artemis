package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.buildagent.config.GenerationSandboxHostingEnabled.MAX_GENERATION_SANDBOX_SLOTS_PROPERTY;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.buildagent.service.RemoteInteractiveSandboxClient;
import de.tum.cit.aet.artemis.buildagent.service.RemoteInteractiveSandboxClient.GenerationSandboxCapacity;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;

/** Reports whether the build-agent fleet advertises exercise-generation sandbox capacity. */
@Component
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionGenerationCapacityHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HyperionGenerationCapacityHealthIndicator.class);

    private static final String CAPACITY_KEY = "generationSandboxCapacity";

    private static final String HINT_KEY = "hint";

    private static final String NO_CAPACITY_HINT = "No build agent advertises Hyperion generation sandbox capacity. Set " + MAX_GENERATION_SANDBOX_SLOTS_PROPERTY
            + " to a positive value on the build agents that should host generation runs.";

    static final Duration REJECTION_WARNING_INTERVAL = Duration.ofMinutes(5);

    private final Optional<RemoteInteractiveSandboxClient> sandboxClient;

    private final AtomicLong lastRejectionWarningAtMillis = new AtomicLong(Long.MIN_VALUE);

    public HyperionGenerationCapacityHealthIndicator(Optional<RemoteInteractiveSandboxClient> sandboxClient) {
        this.sandboxClient = sandboxClient;
    }

    /** Best-effort startup diagnostic; agents may register later, so missing capacity does not fail startup. */
    @EventListener(ApplicationReadyEvent.class)
    public void warnWhenNoBuildAgentAdvertisesGenerationCapacity() {
        capacity().filter(GenerationSandboxCapacity::noAgentAdvertisesCapacity)
                .ifPresent(capacity -> log.warn(
                        "Hyperion exercise generation is enabled but none of the {} reachable build agent(s) advertises generation sandbox capacity, so "
                                + "every generation request will be rejected. Set {} to a positive value on the build agents that should host generation runs.",
                        capacity.reachableAgents(), MAX_GENERATION_SANDBOX_SLOTS_PROPERTY));
    }

    /**
     * Warns, at most once per {@link #REJECTION_WARNING_INTERVAL}, that a generation request was rejected for lack of capacity.
     * <p>
     * Rate limited because an instructor retrying, or a client polling, would otherwise turn a single configuration problem into log noise.
     */
    public void warnGenerationRejectedForMissingCapacity() {
        GenerationSandboxCapacity capacity = capacity().orElse(null);
        if (capacity == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastRejectionWarningAtMillis.get();
        if (last != Long.MIN_VALUE && now - last < REJECTION_WARNING_INTERVAL.toMillis()) {
            return;
        }
        if (!lastRejectionWarningAtMillis.compareAndSet(last, now)) {
            return;
        }
        log.warn("Rejected a Hyperion exercise generation request because no build agent has a free generation sandbox slot ({}). Set {} to a positive value on the build agents "
                + "that should host generation runs.", capacity, MAX_GENERATION_SANDBOX_SLOTS_PROPERTY);
    }

    @Override
    public Health health() {
        GenerationSandboxCapacity capacity = capacity().orElse(null);
        Map<String, Object> details = new LinkedHashMap<>();
        if (capacity == null) {
            details.put(HINT_KEY, "No relay client is available on this node, so generation sandbox capacity cannot be determined.");
            return new ConnectorHealth(false, details).asActuatorHealth();
        }
        details.put(CAPACITY_KEY, capacity.toString());
        if (capacity.noAgentAdvertisesCapacity()) {
            // DOWN rather than UP-with-a-detail: this never resolves on its own, unlike a fleet that is merely busy.
            details.put(HINT_KEY, NO_CAPACITY_HINT);
            return new ConnectorHealth(false, details).asActuatorHealth();
        }
        if (capacity.freeSlots() == 0) {
            details.put(HINT_KEY, "Every advertised generation sandbox slot is currently occupied; new generation requests are rejected until a run finishes.");
        }
        return new ConnectorHealth(true, details).asActuatorHealth();
    }

    private Optional<GenerationSandboxCapacity> capacity() {
        return sandboxClient.map(client -> {
            try {
                return client.generationSandboxCapacity();
            }
            catch (RuntimeException e) {
                log.warn("Could not read the advertised Hyperion generation sandbox capacity", e);
                return null;
            }
        });
    }
}
