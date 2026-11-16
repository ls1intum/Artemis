package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;

class BuildJobContainerCleanupTest {

    @Test
    void interruptionWhileDrainingCleanupDoesNotDiscardTheQueuedKill() throws Exception {
        BuildAgentConfiguration configuration = mock(BuildAgentConfiguration.class);
        DockerClient docker = mock(DockerClient.class);
        when(configuration.getDockerClient()).thenReturn(docker);
        StopContainerCmd stop = mock(StopContainerCmd.class);
        when(docker.stopContainerCmd("cancelled-build")).thenReturn(stop);
        when(stop.withTimeout(15)).thenReturn(stop);
        when(stop.exec()).thenThrow(new IllegalStateException("Stop failed"));
        KillContainerCmd kill = mock(KillContainerCmd.class);
        when(docker.killContainerCmd("cancelled-build")).thenReturn(kill);
        AtomicBoolean killed = new AtomicBoolean();
        doAnswer(invocation -> {
            killed.set(true);
            return null;
        }).when(kill).exec();
        BuildJobContainerService service = new BuildJobContainerService(configuration, mock(BuildLogsMap.class), mock(TempFileUtilService.class));
        CountDownLatch drainStarted = new CountDownLatch(1);
        CountDownLatch drainResumedAfterInterruption = new CountDownLatch(1);
        Semaphore releaseWorker = new Semaphore(0);
        AtomicBoolean firstTask = new AtomicBoolean(true);
        AtomicInteger drainAttempts = new AtomicInteger();
        try (var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>()) {

            @Override
            protected void afterExecute(Runnable task, Throwable failure) {
                // The stop future has completed, but this worker remains occupied so the fallback kill stays queued.
                if (firstTask.getAndSet(false)) {
                    releaseWorker.acquireUninterruptibly();
                }
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                if (drainAttempts.incrementAndGet() == 1) {
                    drainStarted.countDown();
                }
                else {
                    drainResumedAfterInterruption.countDown();
                }
                return super.awaitTermination(timeout, unit);
            }
        }) {
            AtomicBoolean interruptedAfterCleanup = new AtomicBoolean();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                try (var executors = mockStatic(Executors.class)) {
                    executors.when(Executors::newSingleThreadExecutor).thenReturn(executor);
                    service.stopUnresponsiveContainer("cancelled-build");
                    interruptedAfterCleanup.set(Thread.currentThread().isInterrupted());
                }
                catch (Throwable exception) {
                    failure.set(exception);
                }
            });
            try {
                caller.start();
                assertThat(drainStarted.await(20, TimeUnit.SECONDS)).as("cleanup is draining with the fallback kill still queued").isTrue();
                caller.interrupt();
                assertThat(drainResumedAfterInterruption.await(5, TimeUnit.SECONDS)).as("cleanup resumes waiting after interruption").isTrue();
            }
            finally {
                releaseWorker.release();
                caller.join(5_000);
            }

            assertThat(caller.isAlive()).as("cleanup must finish").isFalse();
            assertThat(failure.get()).isNull();
            assertThat(killed).as("the queued fallback kill must run before cleanup returns").isTrue();
            assertThat(interruptedAfterCleanup).as("interruption remains observable after cleanup").isTrue();
        }
    }

    @Test
    void interruptedCancellationFinishesStoppingTheContainerAndRestoresTheInterrupt() throws Exception {
        BuildAgentConfiguration configuration = mock(BuildAgentConfiguration.class);
        DockerClient docker = mock(DockerClient.class);
        when(configuration.getDockerClient()).thenReturn(docker);
        StopContainerCmd stop = mock(StopContainerCmd.class);
        when(docker.stopContainerCmd("cancelled-build")).thenReturn(stop);
        when(stop.withTimeout(15)).thenReturn(stop);
        CountDownLatch stopStarted = new CountDownLatch(1);
        CountDownLatch releaseStop = new CountDownLatch(1);
        AtomicBoolean stopped = new AtomicBoolean();
        doAnswer(invocation -> {
            stopStarted.countDown();
            if (!releaseStop.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("The stop command was never released");
            }
            stopped.set(true);
            return null;
        }).when(stop).exec();
        BuildJobContainerService service = new BuildJobContainerService(configuration, mock(BuildLogsMap.class), mock(TempFileUtilService.class));
        AtomicBoolean interruptedAfterCleanup = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                service.stopUnresponsiveContainer("cancelled-build");
                interruptedAfterCleanup.set(Thread.currentThread().isInterrupted());
            }
            catch (Throwable exception) {
                failure.set(exception);
            }
        });
        try {
            caller.start();
            assertThat(stopStarted.await(5, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            releaseStop.countDown();
            caller.join(5_000);
        }

        assertThat(caller.isAlive()).as("cleanup must finish").isFalse();
        assertThat(failure.get()).isNull();
        assertThat(stopped).isTrue();
        assertThat(interruptedAfterCleanup).as("cancellation remains observable after the container has stopped").isTrue();
    }
}
