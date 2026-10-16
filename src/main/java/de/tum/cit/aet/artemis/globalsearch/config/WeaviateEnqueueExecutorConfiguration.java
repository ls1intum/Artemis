package de.tum.cit.aet.artemis.globalsearch.config;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The executor on which the search index outbox rows of a request are written.
 * <p>
 * The rows say that an entity has to be indexed again, which nobody who edits it waits for, so writing them does not belong on the request thread: each costs a
 * round trip, and an exercise or an exam costs two. Every node that serves requests needs the executor, unlike the one that nudges the dispatcher, which only the
 * scheduling node has.
 * <p>
 * It has one thread so that the rows of one node are written in the order the changes were made. The queue is long enough that it is not expected to fill, and a
 * request that finds it full waits for room, see {@link WaitForRoomPolicy}: running the work on the request thread instead would let it overtake what is queued.
 */
@Configuration
@Lazy
@Conditional(WeaviateEnabled.class)
public class WeaviateEnqueueExecutorConfiguration {

    /**
     * Bean name of the executor, referenced by {@code SearchableEntityWeaviateService}.
     */
    public static final String EXECUTOR_NAME = "weaviateOutboxEnqueueExecutor";

    private static final int QUEUE_CAPACITY = 10_000;

    private static final int SHUTDOWN_WAIT_SECONDS = 10;

    /**
     * Creates the executor. In the {@code test} profile it is a {@link SyncTaskExecutor}, so that a test that changes an entity finds the outbox row it caused before
     * it continues, without awaiting a background thread.
     *
     * @param environment the environment, to recognise the {@code test} profile
     * @return a synchronous executor under the {@code test} profile, otherwise a single threaded pool
     */
    @Bean(EXECUTOR_NAME)
    public Executor weaviateOutboxEnqueueExecutor(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setRejectedExecutionHandler(new WaitForRoomPolicy());
        executor.setThreadNamePrefix("weaviate-enqueue-");
        // Rows still waiting at shutdown are written rather than dropped; whatever is lost anyway is repaired by the reconcile passes.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        return executor;
    }

    /**
     * What happens to a task when the queue is full: the caller waits until there is room and queues it behind the others.
     * <p>
     * The task is neither run by the caller nor dropped. A caller that ran it would put it ahead of the tasks that are still queued, and the
     * rows of two changes to the same entity would be written in the opposite order. The wait is the backpressure of an executor that falls
     * behind, and ends with a {@link RejectedExecutionException} when the executor shuts down or the caller is interrupted.
     */
    public static final class WaitForRoomPolicy implements RejectedExecutionHandler {

        /**
         * Waits for queue capacity while preserving task order. Rechecks shutdown after insertion so a task cannot
         * remain stranded if the last worker stopped while this caller was waiting.
         *
         * @param task     the task that did not fit in the queue
         * @param executor the executor that owns the queue
         * @throws RejectedExecutionException if shutdown or interruption prevents accepting the task
         */
        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("The executor of the search index updates has been shut down");
            }
            try {
                executor.getQueue().put(task);
                // Shutdown may have drained the queue and stopped the worker while this caller waited.
                if (executor.isShutdown() && executor.remove(task)) {
                    throw new RejectedExecutionException("The executor of the search index updates has been shut down");
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting for room to queue a search index update", e);
            }
        }
    }
}
