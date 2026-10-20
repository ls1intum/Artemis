package de.tum.cit.aet.artemis.core.util.junit_extensions;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.task.AsyncTaskExecutor;

/** Tracks shared test work from submission until its runnable actually exits, including the gaps in pool monitoring. */
final class TrackingAsyncTaskExecutor implements AsyncTaskExecutor {

    private final AsyncTaskExecutor delegate;

    private final AtomicInteger pending = new AtomicInteger();

    TrackingAsyncTaskExecutor(AsyncTaskExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task);
        pending.incrementAndGet();
        AtomicBoolean completed = new AtomicBoolean();
        Runnable finish = () -> {
            if (completed.compareAndSet(false, true)) {
                pending.decrementAndGet();
            }
        };
        try {
            delegate.execute(() -> {
                try {
                    task.run();
                }
                finally {
                    finish.run();
                }
            });
        }
        catch (RuntimeException | Error exception) {
            // Rejection has no runnable completion; an inline task that threw has already completed, so release only once.
            finish.run();
            throw exception;
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        FutureTask<Void> future = new FutureTask<>(task, null);
        execute(future);
        return future;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        FutureTask<T> future = new FutureTask<>(task);
        execute(future);
        return future;
    }

    boolean isIdle() {
        return pending.get() == 0;
    }
}
