package de.tum.cit.aet.artemis.core.async;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Inlined replacement for {@code tech.jhipster.async.ExceptionHandlingAsyncTaskExecutor}.
 * <p>
 * A decorator around {@link ThreadPoolTaskExecutor} that logs exceptions thrown
 * by asynchronous tasks instead of silently swallowing them.
 */
public class ExceptionHandlingAsyncTaskExecutor implements AsyncTaskExecutor, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlingAsyncTaskExecutor.class);

    private final ThreadPoolTaskExecutor executor;

    public ExceptionHandlingAsyncTaskExecutor(ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(wrap(task));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return executor.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(wrap(task));
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            }
            catch (Exception e) {
                handle(e);
                throw e;
            }
        };
    }

    private <T> Callable<T> wrap(Callable<T> task) {
        return () -> {
            try {
                return task.call();
            }
            catch (Exception e) {
                handle(e);
                throw e;
            }
        };
    }

    private void handle(Exception e) {
        log.error("Caught async exception", e);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        executor.afterPropertiesSet();
    }

    @Override
    public void destroy() throws Exception {
        executor.destroy();
    }
}
