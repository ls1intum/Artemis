package de.tum.cit.aet.artemis.hyperion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated executor for quiz question pool builds.
 * <p>
 * A build issues one model call per batch of questions and one per generated question, so it runs for minutes and must not
 * occupy the shared task executor. Builds of different courses may run in parallel; builds of the same course are serialised
 * by {@code HyperionQuizQuestionPoolBuildService}.
 */
@Configuration
@Conditional(HyperionEnabled.class)
@Lazy
public class HyperionQuizPoolAsyncConfiguration {

    /**
     * Bounded pool for quiz question pool builds.
     *
     * @return the executor used by {@code HyperionQuizQuestionPoolTaskService.runBuildAsync}
     */
    @Bean("hyperionQuizPoolTaskExecutor")
    public ThreadPoolTaskExecutor hyperionQuizPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("quiz-pool-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
