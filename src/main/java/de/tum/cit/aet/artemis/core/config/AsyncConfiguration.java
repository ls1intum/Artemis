package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
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
        // Run the task on the submitting thread when the pool and its queue are both full, rather than throwing
        // TaskRejectedException at whoever called the @Async method. Most callers of the shared executor are request
        // threads that treat the submission as fire and forget, so a rejection surfaces as a failed request and a lost
        // task. Being as slow as a synchronous call is the right worst case; losing the work is not.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
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

    /**
     * Executor for the version control access log writes (see {@code VcsAccessLogService}).
     * <p>
     * These run on their own pool, and one that discards rather than rejects, because of how the writes are reached.
     * A git push submits the log write from the request thread, so a rejection is thrown at the push, not at the
     * logging: benchmarking a 2000 student exam produced 399 failed pushes this way, each one a student's commit lost
     * to a rejected bookkeeping task. Access logging must never be able to do that.
     * <p>
     * The queue is deliberately short. A long queue on a small pool is worse than a short one, because it delays the
     * point at which the pool is allowed to grow while letting a backlog build that is already stale by the time it
     * drains. When even that is full the entry is dropped with a warning, which loses an audit record but keeps the
     * push working. That is the right way round: the log describes the push, so it cannot be worth more than the push.
     *
     * @return a synchronous executor under the {@code test} profile, otherwise a dedicated pool that discards on
     *         saturation
     */
    @Bean("vcsAccessLogExecutor")
    public Executor vcsAccessLogExecutor() {
        if (environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("vcs-access-log-");
        executor.setRejectedExecutionHandler((runnable, pool) -> log
                .warn("Dropping a version control access log write: the executor is saturated ({} queued). The git operation itself is unaffected.", pool.getQueue().size()));
        return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    /**
     * Executor for writing submission versions (see {@code SubmissionVersionService}).
     * <p>
     * A version is a full copy of the submission content, several kilobytes of {@code longtext}, and nothing in the
     * request reads it back. Writing it on the request thread put the slowest statement in the submit path directly in
     * front of the student, so it moves off the request and the student sees the submission acknowledged sooner.
     * <p>
     * Unlike the access log executor, saturation here runs the task on the calling thread rather than discarding it. A
     * submission version is the student's own work, so the worst acceptable outcome is being as slow as before, never
     * losing it. The queue is bounded so that a backlog cannot grow without limit before that fallback engages.
     *
     * @return a synchronous executor under the {@code test} profile, otherwise a dedicated pool that falls back to the
     *         caller when saturated
     */
    @Bean("submissionVersionExecutor")
    public Executor submissionVersionExecutor() {
        if (environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("submission-version-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    /**
     * Executor for queueing a continuous integration build after a push (see {@code AsyncBuildTriggerService}).
     * <p>
     * Queueing a build is not part of what a git push has to wait for: the push has already written its objects, and
     * nothing in the response depends on the build job existing. It was measured as effectively the whole latency of a
     * push under exam load, so it runs here instead of on the request thread.
     * <p>
     * Saturation runs the task on the calling thread rather than discarding it. A dropped build means a student's commit
     * is never graded, so the worst acceptable outcome is being as slow as before.
     * <p>
     * In the {@code test} profile it is a {@link SyncTaskExecutor}, so tests that push and then assert on the resulting
     * build job stay deterministic.
     *
     * @return a synchronous executor under the {@code test} profile, otherwise a dedicated pool that falls back to the
     *         caller when saturated
     */
    @Bean("buildTriggerExecutor")
    public Executor buildTriggerExecutor() {
        if (environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Deliberately small. Queueing a build competes with request handling for database connections and processor
        // time, and a pool wide enough to absorb an exam's worth of pushes at once starves the endpoints students are
        // waiting on. Kept narrow, the caller-runs fallback pushes the work back onto the git thread that produced it,
        // which is where the natural backpressure was before this became asynchronous.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("build-trigger-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
