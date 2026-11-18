package de.tum.cit.aet.artemis.globalsearch.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Own thread for the outbox dispatcher's enqueue nudge.
 * <p>
 * The shared {@code taskExecutor} runs a task on the caller's thread when it is saturated, which would let a drain, with
 * its index writes and embedding requests, run on a user's request thread. This executor has one thread and room for
 * one waiting nudge; further nudges are dropped, which loses nothing because a nudge only marks that a drain is pending
 * and the running drain picks that mark up.
 */
@Configuration
@Lazy
@Conditional(WeaviateEnabled.class)
@Profile(PROFILE_SCHEDULING)
public class WeaviateDispatcherExecutorConfiguration {

    /**
     * Bean name of the dispatcher's executor, referenced by {@code @Async}.
     */
    public static final String EXECUTOR_NAME = "weaviateOutboxDispatcherExecutor";

    /**
     * Single-thread executor that discards a nudge when one is already waiting.
     *
     * @return the executor used by {@code WeaviateOutboxDispatcher.onOutboxEnqueued}
     */
    @Bean(EXECUTOR_NAME)
    public ThreadPoolTaskExecutor weaviateOutboxDispatcherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setThreadNamePrefix("weaviate-outbox-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
