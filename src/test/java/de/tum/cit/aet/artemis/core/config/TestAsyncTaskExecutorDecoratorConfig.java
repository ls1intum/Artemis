package de.tum.cit.aet.artemis.core.config;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.AsyncTaskExecutor;

import de.tum.cit.aet.artemis.programming.service.RepositoryUriConversionUtil;

/**
 * Test-profile decorator that propagates the version-control base URL
 * into every @Async task thread without touching AsyncConfiguration.
 */
@Configuration
@Profile(SPRING_PROFILE_TEST)
@Lazy
public class TestAsyncTaskExecutorDecoratorConfig implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(TestAsyncTaskExecutorDecoratorConfig.class);

    private final String vcsBaseUrl;

    public TestAsyncTaskExecutorDecoratorConfig(@Value("${artemis.version-control.url}") String vcsBaseUrl) {
        this.vcsBaseUrl = vcsBaseUrl;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // Only wrap the async executor bean produced by AsyncConfiguration
        if ("taskExecutor".equals(beanName)) {
            // Unwrap proxies to see if it implements AsyncTaskExecutor
            Object target = bean;
            if (AopUtils.isAopProxy(bean)) {
                target = AopUtils.getTargetClass(bean);
            }
            if (bean instanceof AsyncTaskExecutor asyncDelegate) {
                log.debug("Wrapping '{}' with VCS base URL propagation for tests", beanName);
                return new DecoratingAsyncTaskExecutor(asyncDelegate, vcsBaseUrl);
            }
            if (bean instanceof Executor simpleDelegate) {
                log.debug("Wrapping '{}' (Executor) with VCS base URL propagation for tests", beanName);
                return new DecoratingExecutor(simpleDelegate, vcsBaseUrl);
            }
        }
        return bean;
    }

    /** Wraps an AsyncTaskExecutor to seed/clear the RepositoryUriConversionUtil override per task. */
    static final class DecoratingAsyncTaskExecutor implements AsyncTaskExecutor {

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
        public Future<?> submit(Runnable task) {
            return delegate.submit(decorate(task));
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(decorate(task));
        }

        @Override
        public void execute(Runnable task) {
            delegate.execute(decorate(task));
        }
    }

    /** Fallback wrapper if the bean is only an Executor (not AsyncTaskExecutor). */
    static final class DecoratingExecutor implements Executor {

        private final Executor delegate;

        private final String baseUrl;

        DecoratingExecutor(Executor delegate, String baseUrl) {
            this.delegate = delegate;
            this.baseUrl = baseUrl;
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(() -> {
                RepositoryUriConversionUtil.overrideServerUrlForCurrentThread(baseUrl);
                try {
                    command.run();
                }
                finally {
                    RepositoryUriConversionUtil.clearServerUrlOverrideForCurrentThread();
                }
            });
        }
    }
}
