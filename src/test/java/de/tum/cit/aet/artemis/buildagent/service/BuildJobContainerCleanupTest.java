package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.StopContainerCmd;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;

class BuildJobContainerCleanupTest {

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
