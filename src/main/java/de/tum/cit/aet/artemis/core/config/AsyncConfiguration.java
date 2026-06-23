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

    public AsyncConfiguration(TaskExecutionProperties taskExecutionProperties) {
        this.taskExecutionProperties = taskExecutionProperties;
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
     * Executor used for sending e-mails (see the mail sending service).
     * <p>
     * In production this returns the shared {@code taskExecutor}, so mail delivery behaves exactly as before: asynchronous
     * on the general async pool. In the {@code test} profile it returns a {@link SyncTaskExecutor} so that e-mails are sent
     * on the calling thread instead of a background thread. This guarantees the shared {@code JavaMailSender} spy is never
     * invoked by a background thread while a test re-stubs or resets that spy between tests. Such concurrent access
     * corrupts Mockito's internal state and surfaces as a flaky {@code UnfinishedStubbingException} in an unrelated test's
     * {@code @BeforeEach} mock setup.
     *
     * @param environment  the Spring environment, used to detect the test profile
     * @param taskExecutor the general async executor, reused for mail in production
     * @return a synchronous executor under the test profile, otherwise the shared async executor
     */
    @Bean("mailTaskExecutor")
    public Executor mailTaskExecutor(Environment environment, @Qualifier("taskExecutor") Executor taskExecutor) {
        if (environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return new SyncTaskExecutor();
        }
        return taskExecutor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
