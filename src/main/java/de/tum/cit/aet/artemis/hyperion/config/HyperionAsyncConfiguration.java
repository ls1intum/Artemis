package de.tum.cit.aet.artemis.hyperion.config;

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

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;

/**
 * Wiring for Hyperion's agentic generation infrastructure: the dedicated generation executor and the task-agnostic {@link AgentLoopRunner} bean.
 * <p>
 * A generation run is long-lived and mostly blocked (on LLM turns and sandbox builds) and publishes progress over the websocket, whose delivery runs on the shared
 * {@code taskExecutor}. Running generation on that same pool risks it deadlocking against its own websocket sends and starving other async work, so it gets its own bounded pool.
 */
@Lazy
@Configuration
@Conditional(HyperionEnabled.class)
public class HyperionAsyncConfiguration {

    /**
     * Wires the task-agnostic {@link AgentLoopRunner} as a Hyperion-conditional bean, supplying the deployment's context-window size.
     *
     * @param chatModels          the available chat models (the first is used; empty if no AI provider is configured)
     * @param contextWindowTokens the model's usable context window in tokens, below which the loop keeps the conversation via compaction
     * @return the agent loop runner
     */
    @Bean
    @Lazy
    public AgentLoopRunner agentLoopRunner(Collection<ChatModel> chatModels, @Value("${artemis.hyperion.agent.context-window-tokens:128000}") int contextWindowTokens) {
        return new AgentLoopRunner(chatModels, contextWindowTokens);
    }

    /**
     * @return the bounded executor that runs {@code ExerciseGenerationTaskService.runAsync}. Per-exercise single-flight already bounds duplicate work; this bounds total
     *         concurrent generations on a node and keeps them off the shared task executor.
     */
    @Bean(name = "hyperionGenerationExecutor")
    public Executor hyperionGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("hyperion-gen-");
        // On saturation, abort (throw RejectedExecutionException) rather than run on the caller: the submit path is the HTTP request thread, and CallerRunsPolicy would block it
        // for
        // the full multi-minute generation. Aborting lets the REST layer map the rejection to 429/Conflict and shed load.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
