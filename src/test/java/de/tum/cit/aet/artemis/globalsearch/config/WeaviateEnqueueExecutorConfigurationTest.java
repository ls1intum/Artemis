package de.tum.cit.aet.artemis.globalsearch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnqueueExecutorConfiguration.WaitForRoomPolicy;

/**
 * The outbox rows of one node have to be written in the order the changes were made, also when the executor has fallen behind.
 */
class WeaviateEnqueueExecutorConfigurationTest {

    private static final int SECONDS = 10;

    private final List<Integer> executed = Collections.synchronizedList(new ArrayList<>());

    @Test
    void aFullQueueMakesTheCallerWaitInsteadOfLettingItsTaskOvertakeTheQueuedOnes() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), new WaitForRoomPolicy());
        ExecutorService caller = Executors.newSingleThreadExecutor();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            // task 1 occupies the only thread, task 2 fills the queue
            executor.execute(() -> {
                workerStarted.countDown();
                await(releaseWorker);
                executed.add(1);
            });
            assertThat(workerStarted.await(SECONDS, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> executed.add(2));

            // task 3 finds the queue full: it must wait, and not run on the thread that submits it
            Future<?> third = caller.submit(() -> executor.execute(() -> executed.add(3)));
            assertThatThrownBy(() -> third.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(executed).as("nothing overtook the queued tasks").isEmpty();

            releaseWorker.countDown();
            third.get(SECONDS, TimeUnit.SECONDS);
            executor.shutdown();
            assertThat(executor.awaitTermination(SECONDS, TimeUnit.SECONDS)).isTrue();

            assertThat(executed).containsExactly(1, 2, 3);
        }
        finally {
            releaseWorker.countDown();
            caller.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test
    void shutdownWhileWaitingForRoomRejectsTheTaskInsteadOfLeavingItWithoutAWorker() throws Exception {
        CountDownLatch callerWaiting = new CountDownLatch(1);
        CountDownLatch allowInsertion = new CountDownLatch(1);
        ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1) {

            @Override
            public void put(Runnable task) throws InterruptedException {
                callerWaiting.countDown();
                if (!allowInsertion.await(SECONDS, TimeUnit.SECONDS)) {
                    throw new InterruptedException("Timed out waiting to insert the task");
                }
                super.put(task);
            }
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, queue, new WaitForRoomPolicy());
        ExecutorService caller = Executors.newSingleThreadExecutor();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                workerStarted.countDown();
                await(releaseWorker);
            });
            assertThat(workerStarted.await(SECONDS, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> executed.add(1));
            Future<?> submission = caller.submit(() -> executor.execute(() -> executed.add(2)));
            assertThat(callerWaiting.await(SECONDS, TimeUnit.SECONDS)).isTrue();

            executor.shutdownNow();
            assertThat(executor.awaitTermination(SECONDS, TimeUnit.SECONDS)).isTrue();
            allowInsertion.countDown();

            assertThatThrownBy(() -> submission.get(SECONDS, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(RejectedExecutionException.class);
            assertThat(queue).isEmpty();
            assertThat(executed).isEmpty();
        }
        finally {
            allowInsertion.countDown();
            releaseWorker.countDown();
            caller.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test
    void aShutDownExecutorRejectsTheTask() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), new WaitForRoomPolicy());
        executor.shutdown();

        assertThatThrownBy(() -> executor.execute(() -> executed.add(1))).isInstanceOf(RejectedExecutionException.class);
        assertThat(executed).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(SECONDS, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
