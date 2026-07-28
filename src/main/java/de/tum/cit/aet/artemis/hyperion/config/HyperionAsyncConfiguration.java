package de.tum.cit.aet.artemis.hyperion.config;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentCheckpointManager;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.ProviderFailureCooldown;

/**
 * Wiring for Hyperion's agentic generation infrastructure: the dedicated generation executor and the task-agnostic {@link AgentLoopRunner} bean.
 * <p>
 * A generation run is long-lived and mostly blocked (on LLM turns and sandbox builds) and publishes progress over the websocket, whose delivery runs on the shared
 * {@code taskExecutor}. Running generation on that same pool risks it deadlocking against its own websocket sends and starving other async work, so it gets its own bounded pool.
 */
@Lazy
@Configuration
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionAsyncConfiguration {

    /**
     * Wires the task-agnostic {@link AgentLoopRunner} as an exercise-generation-conditional bean, supplying the deployment's context-window size.
     *
     * @param chatModels                  the available chat models (the first is used; empty if no AI provider is configured)
     * @param contextWindowTokens         the model's usable context window in tokens, below which the loop keeps the conversation via compaction
     * @param providerHardFailureCooldown cooldown applied after deterministic provider/auth/quota failures
     * @param providerFailureCooldown     shared provider cooldown state
     * @param checkpointManager           opt-in development checkpoint manager
     * @return the agent loop runner
     */
    @Bean
    @Lazy
    public AgentLoopRunner agentLoopRunner(Collection<ChatModel> chatModels, @Value("${artemis.hyperion.agent.context-window-tokens:128000}") int contextWindowTokens,
            @Value("${artemis.hyperion.agent.provider-hard-failure-cooldown:PT5M}") Duration providerHardFailureCooldown, ProviderFailureCooldown providerFailureCooldown,
            AgentCheckpointManager checkpointManager) {
        return new AgentLoopRunner(chatModels, contextWindowTokens, providerHardFailureCooldown, providerFailureCooldown, checkpointManager);
    }

    /**
     * @param maxConcurrentJobsPerNode node-local generation concurrency; excess starts fail fast instead of waiting in memory
     * @return the bounded executor that runs {@code GenerationTaskService.runAsync}. Per-exercise single-flight already bounds duplicate work; this bounds total
     *         concurrent generations on a node and keeps them off the shared task executor.
     */
    @Bean(name = "hyperionGenerationExecutor")
    public Executor hyperionGenerationExecutor(@Value("${artemis.hyperion.generation.max-concurrent-jobs-per-core-node:2}") int maxConcurrentJobsPerNode) {
        if (maxConcurrentJobsPerNode < 1) {
            throw new IllegalArgumentException("artemis.hyperion.generation.max-concurrent-jobs-per-core-node must be at least 1");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Keep generation admission honest: these jobs are long-lived and already hold a per-exercise slot, so do not accept a deep in-memory backlog that can wait far longer
        // than the configured generation deadline before it even starts.
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
