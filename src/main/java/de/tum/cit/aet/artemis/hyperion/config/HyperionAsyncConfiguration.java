package de.tum.cit.aet.artemis.hyperion.config;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated core executor for worker coordination and guarded finalization. Model execution stays on the worker.
 * <p>
 * A generation run is long-lived and mostly blocked (on LLM turns and sandbox builds) and publishes progress over the websocket, whose delivery runs on the shared
 * {@code taskExecutor}. Running generation on that same pool risks it deadlocking against its own websocket sends and starving other async work, so it gets its own bounded pool.
 */
@Lazy
@Configuration
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionAsyncConfiguration {

    /**
     * @param maxConcurrentJobsPerNode node-local generation concurrency; excess starts fail fast instead of waiting in memory
     * @param shutdownGuard            knows which runs have passed their point of no return and must not be interrupted by a rolling deploy
     * @param shutdownDrainTimeout     how long shutdown waits for those runs; size it to the persistence path, whose longest leg is
     *                                     {@code artemis.hyperion.generation.test-case-sync-timeout}
     * @return the bounded executor that runs {@code GenerationTaskService.runAsync}. Per-exercise single-flight already bounds duplicate work; this bounds total
     *         concurrent generations on a node and keeps them off the shared task executor.
     */
    @Bean(name = "hyperionGenerationExecutor")
    public Executor hyperionGenerationExecutor(@Value("${artemis.hyperion.generation.max-concurrent-jobs-per-core-node:2}") int maxConcurrentJobsPerNode,
            GenerationShutdownGuard shutdownGuard, @Value("${artemis.hyperion.generation.shutdown-drain-timeout:PT11M}") Duration shutdownDrainTimeout) {
        if (maxConcurrentJobsPerNode < 1) {
            throw new IllegalArgumentException("artemis.hyperion.generation.max-concurrent-jobs-per-core-node must be at least 1");
        }
        if (shutdownDrainTimeout == null || shutdownDrainTimeout.isNegative()) {
            throw new IllegalArgumentException("artemis.hyperion.generation.shutdown-drain-timeout must not be negative");
        }
        ThreadPoolTaskExecutor executor = new HyperionGenerationExecutor(shutdownGuard, shutdownDrainTimeout);
        // No queue: a buffered generation could wait past its own deadline before it even starts, so excess starts are rejected rather than backlogged.
        executor.setCorePoolSize(maxConcurrentJobsPerNode);
        executor.setMaxPoolSize(maxConcurrentJobsPerNode);
        executor.setQueueCapacity(0);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("hyperion-gen-");
        // On saturation, abort rather than block the request thread for the full generation. The REST layer reports that generation capacity is unavailable.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
