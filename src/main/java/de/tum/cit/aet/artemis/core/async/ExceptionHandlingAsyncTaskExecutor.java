package de.tum.cit.aet.artemis.core.async;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
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
 * Only the {@link Authentication} is carried over, into a context object created for the task. Sharing the submitter's
 * context object instead would let the two threads write to the same holder: a task that calls
 * {@code SecurityUtils.setAuthorizationObject()} would install its {@code ROLE_ADMIN} stand-in into the submitter's
 * context, handing that authority to a thread that never had it.
 *
 * <p>
 * The executing thread's previous context is restored once the task finishes rather than cleared, which matters
 * because four of the executors are configured with {@code CallerRunsPolicy}: when the pool saturates, the task runs
 * inline on the submitting thread, and clearing would strip the authentication of the request that submitted it. On an
 * ordinary pooled worker there is nothing to restore, so the thread is left without an authentication either way, and
 * the next task cannot inherit this one's principal. That leftover is what makes background work non-deterministic,
 * and it is why entry points that genuinely have no caller use
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
        // Read here, on the submitting thread, rather than inside the lambda, which already runs on the worker.
        Authentication callerAuthentication = SecurityContextHolder.getContext().getAuthentication();
        return () -> {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(contextFor(callerAuthentication));
            try {
                task.run();
            }
            catch (Exception e) {
                handle(e);
                throw e;
            }
            finally {
                SecurityContextHolder.setContext(previousContext);
            }
        };
    }

    private <T> Callable<T> wrap(Callable<T> task) {
        Authentication callerAuthentication = SecurityContextHolder.getContext().getAuthentication();
        return () -> {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(contextFor(callerAuthentication));
            try {
                return task.call();
            }
            catch (Exception e) {
                handle(e);
                throw e;
            }
            finally {
                SecurityContextHolder.setContext(previousContext);
            }
        };
    }

    /**
     * @param authentication the submitting thread's authentication, may be {@code null}
     * @return a context of the task's own, so that what the task installs cannot reach the submitter
     */
    private static SecurityContext contextFor(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
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
