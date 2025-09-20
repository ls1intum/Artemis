package de.tum.cit.aet.artemis.core.config;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.AsyncTaskExecutor;

import de.tum.cit.aet.artemis.programming.service.RepositoryUriConversionUtil;

/**
 * Test-only configuration that decorates the central Spring {@code taskExecutor} with
 * a {@link RepositoryUriConversionUtil} override for each async task thread.
 *
 * <p>
 * Why this is necessary:
 * </p>
 * <ul>
 * <li>During parallel test execution, multiple Spring contexts may be active at once,
 * each configured with a different {@code artemis.version-control.url}.</li>
 * <li>{@link RepositoryUriConversionUtil} uses a {@code ThreadLocal} to hold the
 * effective base URL for repository conversions in that thread.</li>
 * <li>Without this decorator, {@code @Async} tasks may run in a thread that does not
 * have the correct URL set, leading to nondeterministic test failures.</li>
 * </ul>
 *
 * <p>
 * What this class does:
 * </p>
 * <ul>
 * <li>Intercepts creation of the {@code taskExecutor} bean in the test profile.</li>
 * <li>Wraps it with a delegating executor that injects the current test’s
 * {@code artemis.version-control.url} into {@link RepositoryUriConversionUtil}
 * before running each task.</li>
 * <li>Ensures cleanup by clearing the {@code ThreadLocal} after the task completes.</li>
 * </ul>
 */
@Configuration
@Profile(SPRING_PROFILE_TEST)
@Lazy
public class TestAsyncTaskExecutorDecoratorConfig implements BeanPostProcessor {

    private final String vcsBaseUrl;

    public TestAsyncTaskExecutorDecoratorConfig(@Value("${artemis.version-control.url}") String vcsBaseUrl) {
        this.vcsBaseUrl = vcsBaseUrl;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if ("taskExecutor".equals(beanName) && bean instanceof AsyncTaskExecutor asyncDelegate) {
            return new DecoratingAsyncTaskExecutor(asyncDelegate, vcsBaseUrl);
        }
        return bean;
    }

    /**
     * Executor wrapper that decorates every submitted task with logic to set and clear
     * the {@link RepositoryUriConversionUtil} server URL for the current thread.
     */
    private static final class DecoratingAsyncTaskExecutor implements AsyncTaskExecutor {

        private final AsyncTaskExecutor delegate;

        private final String baseUrl;

        DecoratingAsyncTaskExecutor(AsyncTaskExecutor delegate, String baseUrl) {
            this.delegate = delegate;
            this.baseUrl = baseUrl;
        }

        private Runnable decorate(Runnable task) {
            return () -> {
                RepositoryUriConversionUtil.overrideServerUrlForCurrentThread(baseUrl);
                try {
                    task.run();
                }
                finally {
                    RepositoryUriConversionUtil.clearServerUrlOverrideForCurrentThread();
                }
            };
        }

        private <T> Callable<T> decorate(Callable<T> task) {
            return () -> {
                RepositoryUriConversionUtil.overrideServerUrlForCurrentThread(baseUrl);
                try {
                    return task.call();
                }
                finally {
                    RepositoryUriConversionUtil.clearServerUrlOverrideForCurrentThread();
                }
            };
        }

        @Override
        public void execute(Runnable task, long startTimeout) {
            delegate.execute(decorate(task), startTimeout);
        }

        @Override
        public void execute(Runnable task) {
            delegate.execute(decorate(task));
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegate.submit(decorate(task));
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(decorate(task));
        }
    }
}
