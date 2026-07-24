package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import de.tum.cit.aet.artemis.core.async.ExceptionHandlingAsyncTaskExecutor;

@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Configuration
@Lazy(false)
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
public class AsyncConfiguration implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfiguration.class);

    private final TaskExecutionProperties taskExecutionProperties;

    private final Environment environment;

    public AsyncConfiguration(TaskExecutionProperties taskExecutionProperties, Environment environment) {
        this.taskExecutionProperties = taskExecutionProperties;
        this.environment = environment;
    }

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        log.debug("Creating Async Task Executor");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(taskExecutionProperties.getPool().getCoreSize());
        executor.setMaxPoolSize(taskExecutionProperties.getPool().getMaxSize());
        executor.setQueueCapacity(taskExecutionProperties.getPool().getQueueCapacity());
        executor.setThreadNamePrefix(taskExecutionProperties.getThreadNamePrefix());
        return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    /**
     * Dedicated executor for Deimos batch runs to isolate long-running LLM workloads from the shared async infrastructure.
     *
     * @param corePoolSize  minimum number of threads kept in the Deimos executor
     * @param maxPoolSize   maximum number of threads used by the Deimos executor
     * @param queueCapacity maximum number of queued Deimos batch tasks
     * @return async executor dedicated to Deimos workloads
     */
    @Bean(name = "deimosTaskExecutor")
    public Executor deimosTaskExecutor(@Value("${artemis.deimos.executor.core-pool-size:2}") int corePoolSize, @Value("${artemis.deimos.executor.max-pool-size:4}") int maxPoolSize,
            @Value("${artemis.deimos.executor.queue-capacity:25}") int queueCapacity) {
        log.debug("Creating Deimos Async Task Executor (core={}, max={}, queue={})", corePoolSize, maxPoolSize, queueCapacity);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("deimos-task-");
        return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    /**
     * Executor for the {@code @Async} mail methods (see the mail sending service).
     * <p>
     * In production this delegates to the shared {@code taskExecutor}, so mail is sent asynchronously on the same thread
     * pool as before. It deliberately returns a thin delegating {@link Executor} (a method reference) rather than the
     * {@code taskExecutor} bean instance itself: that instance is an {@link ExceptionHandlingAsyncTaskExecutor}, which is
     * an {@code InitializingBean}/{@code DisposableBean}. Exposing it again under a second bean name would make Spring run
     * its lifecycle callbacks a second time and initialize a second, orphaned thread pool. The delegate has no lifecycle,
     * so production behavior is unchanged (mail still runs on the shared pool, asynchronously).
     * <p>
     * In the {@code test} profile it is a {@link SyncTaskExecutor}: mail is sent on the calling thread so the shared
     * {@code JavaMailSender} spy is never invoked by a background thread while a test stubs or resets it (which corrupts
     * Mockito's state and surfaces as a flaky {@code UnfinishedStubbingException}). Only mail is affected; every other
     * {@code @Async} task keeps using the real executor.
     *
     * @param taskExecutor the shared async executor, delegated to for mail in production
     * @return a synchronous executor under the {@code test} profile, otherwise a thin delegate to the shared executor
     */
    @Bean("mailTaskExecutor")
    public Executor mailTaskExecutor(@Qualifier("taskExecutor") Executor taskExecutor) {
        if (environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return new SyncTaskExecutor();
        }
        return taskExecutor::execute;
    }

    /**
     * Executor for asynchronous exercise versioning (see {@code ExerciseVersionService}).
     * <p>
     * In production this is a dedicated, bounded thread pool rather than a delegate to the shared {@code taskExecutor}.
     * Versioning involves potentially slow git access; isolating it in its own pool prevents that slow work from
     * exhausting the shared pool and starving unrelated {@code @Async} tasks (and vice versa).
     * <p>
     * In the {@code test} profile it is a {@link SyncTaskExecutor} so versioning runs on the calling thread. This keeps
     * the many tests that trigger versioning (directly or through a REST call) deterministic: the exercise version is
     * created before the test continues, without having to await a background thread.
     *
     * @return a synchronous executor under the {@code test} profile, otherwise a dedicated bounded thread pool
     */
    @Bean("exerciseVersionTaskExecutor")
    public Executor exerciseVersionTaskExecutor() {
        if (environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(taskExecutionProperties.getPool().getQueueCapacity());
        executor.setThreadNamePrefix("exercise-versioning-");
        return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    /**
     * Executor for asynchronous quiz statistics updates (see {@code QuizSubmissionService}).
     * <p>
     * In production this is a dedicated, single-threaded executor rather than a delegate to the shared
     * {@code taskExecutor}. It isolates the statistics work from the shared pool and, by using a single worker,
     * serializes all statistics updates on a node so same-node updates cannot race. The incremental update itself is
     * the same mechanism used for live and exam quiz submissions. Statistics are only relevant for instructors, so the
     * student's submission request does not wait for this work.
     * <p>
     * In the {@code test} profile it is a {@link SyncTaskExecutor} so the statistics update runs on the calling thread,
     * keeping tests that assert on quiz statistics deterministic.
     *
     * @return a synchronous executor under the {@code test} profile, otherwise a dedicated single-threaded executor
     */
    @Bean("quizStatisticsTaskExecutor")
    public Executor quizStatisticsTaskExecutor() {
        if (environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Single worker: serializes incremental statistics updates so concurrent updates for the same quiz cannot
        // overwrite each other's counter changes.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(taskExecutionProperties.getPool().getQueueCapacity());
        executor.setThreadNamePrefix("quiz-statistics-");
        return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
