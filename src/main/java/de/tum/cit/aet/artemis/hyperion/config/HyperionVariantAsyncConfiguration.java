package de.tum.cit.aet.artemis.hyperion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated executor for exercise-variant generation jobs.
 *
 * Variant jobs are multi-minute tasks (LLM calls + CI builds). Running them on the shared
 * {@code taskExecutor} is not an option: its pool has {@code core-size: 2} with a 10000-deep queue, so a
 * {@link ThreadPoolTaskExecutor} never grows past two threads — two variant jobs would starve every other
 * async task in Artemis, and a third variant job would queue until one finishes. Instructors must be able
 * to generate several variants simultaneously (an instructor generating three variants of an exercise must
 * not wait for each one to finish), so variant jobs get their own bounded pool.
 */
@Configuration
@Conditional(HyperionEnabled.class)
@Lazy
public class HyperionVariantAsyncConfiguration {

    /**
     * Bounded pool for variant-generation jobs: enough threads for realistic parallel generation per node,
     * a small queue so excess jobs wait briefly instead of being rejected.
     *
     * @return the executor used by {@code ExerciseVariantTaskService.runJobAsync}
     */
    @Bean("hyperionVariantTaskExecutor")
    public ThreadPoolTaskExecutor hyperionVariantTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("variant-job-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
