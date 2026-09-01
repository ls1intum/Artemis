package de.tum.cit.aet.artemis.core.async;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Inlined replacement for {@code tech.jhipster.async.ExceptionHandlingAsyncTaskExecutor}.
 * <p>
 * A decorator around {@link ThreadPoolTaskExecutor} that logs exceptions thrown by asynchronous tasks instead of
 * silently swallowing them, and that carries the submitting thread's {@link SecurityContext} over to the worker.
 * <p>
 * Every executor bean in {@code AsyncConfiguration} is wrapped in this class, which is what makes it the one place
 * where the context hand-over can be established. Without it the context is simply lost: Spring does not propagate it
 * to a {@code @Async} method, so work a user triggered would run with no identity at all, and auditing would attribute
 * whatever it saves to the system account rather than to the person who acted.
 * <p>
 * The context is also <b>cleared</b> once the task finishes. Worker threads are pooled and nothing else clears them,
 * so without this the next task on the same thread would inherit the previous one's principal. That leftover is what
 * makes background work non-deterministic, and it is why entry points that genuinely have no caller use
 * {@link de.tum.cit.aet.artemis.core.security.SecurityUtils#runAsSystem} rather than relying on whatever is present.
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
        // Captured here, on the submitting thread, rather than inside the lambda, which already runs on the worker.
        SecurityContext callerContext = SecurityContextHolder.getContext();
        return () -> {
            SecurityContextHolder.setContext(callerContext);
            try {
                task.run();
            }
            catch (Exception e) {
                handle(e);
                throw e;
            }
            finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private <T> Callable<T> wrap(Callable<T> task) {
        SecurityContext callerContext = SecurityContextHolder.getContext();
        return () -> {
            SecurityContextHolder.setContext(callerContext);
            try {
                return task.call();
            }
            catch (Exception e) {
                handle(e);
                throw e;
            }
            finally {
                SecurityContextHolder.clearContext();
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
