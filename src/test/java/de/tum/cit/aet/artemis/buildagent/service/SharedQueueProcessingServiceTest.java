package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

class SharedQueueProcessingServiceTest {

    @Test
    void pauseWaitsForAdmittedGenerationCreateAndThenRejectsNewCreates() throws InterruptedException {
        SharedQueueProcessingService service = new SharedQueueProcessingService(mock(BuildAgentConfiguration.class), mock(BuildJobManagementService.class),
                mock(BuildLogsMap.class), mock(TaskScheduler.class), mock(BuildAgentDockerService.class), mock(BuildJobContainerService.class),
                mock(BuildAgentInformationService.class), mock(DistributedDataAccessService.class));

        assertThat(service.tryAcquireGenerationAdmission()).isTrue();
        CountDownLatch pauseStarted = new CountDownLatch(1);
        AtomicReference<Thread> pauseThread = new AtomicReference<>();
        CompletableFuture<Void> pause = CompletableFuture.runAsync(() -> {
            pauseThread.set(Thread.currentThread());
            pauseStarted.countDown();
            service.setPauseState(true);
        });
        try {
            assertThat(pauseStarted.await(2, TimeUnit.SECONDS)).isTrue();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> pauseThread.get().getState() == Thread.State.WAITING);
            assertThat(pause).isNotDone();
        }
        finally {
            service.releaseGenerationAdmission();
        }

        assertThat(pause).succeedsWithin(Duration.ofSeconds(2));
        assertThat(service.tryAcquireGenerationAdmission()).isFalse();
    }
}
