package de.tum.cit.aet.artemis.hyperion.config;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService;

/** Reports dedicated generation-worker capacity without involving LocalCI build agents. */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionGenerationCapacityHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HyperionGenerationCapacityHealthIndicator.class);

    private final GenerationWorkerRegistryService workers;

    private final AtomicLong lastWarning = new AtomicLong();

    public HyperionGenerationCapacityHealthIndicator(GenerationWorkerRegistryService workers) {
        this.workers = workers;
    }

    /** Emits a rate-limited capacity diagnostic without logging credentials or job content. */
    public void warnGenerationRejectedForMissingCapacity() {
        long now = System.currentTimeMillis();
        long previous = lastWarning.get();
        if (now - previous > 300_000 && lastWarning.compareAndSet(previous, now)) {
            log.warn("Hyperion generation has no available worker. Check the generation broker, worker image and worker readiness.");
        }
    }

    @Override
    public Health health() {
        int reachable = workers.reachableWorkers();
        int available = workers.availableWorkers();
        int capable = workers.capableWorkers();
        return (capable == 0 ? Health.down() : Health.up()).withDetail("reachableWorkers", reachable).withDetail("capableWorkers", capable)
                .withDetail("availableWorkers", available).build();
    }
}
