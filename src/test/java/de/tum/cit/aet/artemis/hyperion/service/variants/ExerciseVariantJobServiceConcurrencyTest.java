package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;

/**
 * Regression test for a lost-update race: {@code heartbeat()} used to do its own independent get-modify-put on
 * the distributed job map, completely bypassing {@code mutate()}'s locking — a heartbeat landing between
 * {@code requestCancel()}'s read and write could silently revert the cancel flag back to false, since whichever
 * writer's {@code put()} landed last would overwrite the other's stale-copy change. Both now share one per-key
 * {@link de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap#lock} , making the two mutually
 * exclusive. Uses a real {@link LocalDataProviderService} (not a mock) — a mocked map would not exercise real
 * per-key locking semantics, and this bug only reproduces under genuine concurrent access to the same map entry.
 * The race is between two threads in one JVM, so the in-process provider reproduces it exactly as a clustered
 * backend would, without paying for an embedded cluster member.
 */
class ExerciseVariantJobServiceConcurrencyTest {

    private ExerciseVariantJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new ExerciseVariantJobService(new LocalDataProviderService(), mock(HyperionWebsocketService.class));
        jobService.init();
    }

    @RepeatedTest(20)
    void concurrentHeartbeatsMustNeverRevertACancelRequest() throws InterruptedException {
        Exercise exercise = mock(Exercise.class);
        when(exercise.getId()).thenReturn(1L);
        when(exercise.getTitle()).thenReturn("Test Exercise");
        when(exercise.getExerciseType()).thenReturn(ExerciseType.PROGRAMMING);
        User user = mock(User.class);
        when(user.getLogin()).thenReturn("instructor1");
        VariantJob job = jobService.startJob(user, exercise, mock(VariantGenerationRequestDTO.class));

        int heartbeatThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(heartbeatThreads + 1);
        CountDownLatch ready = new CountDownLatch(heartbeatThreads + 1);
        CountDownLatch go = new CountDownLatch(1);
        try {
            for (int i = 0; i < heartbeatThreads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(go);
                    for (int call = 0; call < 200; call++) {
                        jobService.heartbeat(job.getJobId());
                    }
                });
            }
            executor.submit(() -> {
                ready.countDown();
                awaitUninterruptibly(go);
                jobService.requestCancel(job.getJobId(), "instructor1");
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(jobService.isCancelRequested(job.getJobId())).isTrue();
    }

    /**
     * The same map entry, the other direction: {@code requestCancel} accepts while the phase is still
     * cancellable, so a plain {@code updatePhase(FINALIZING)} could enter the phase after a cancel had already
     * been answered with success — the job would then finish despite the accepted cancellation. Both writes now
     * take the job's lock, so exactly one of the two must win.
     */
    @RepeatedTest(20)
    void aCancelRequestAndTheFinalizingTransitionMustNotBothSucceed() throws InterruptedException {
        Exercise exercise = mock(Exercise.class);
        when(exercise.getId()).thenReturn(1L);
        when(exercise.getTitle()).thenReturn("Test Exercise");
        when(exercise.getExerciseType()).thenReturn(ExerciseType.PROGRAMMING);
        User user = mock(User.class);
        when(user.getLogin()).thenReturn("instructor1");
        VariantJob job = jobService.startJob(user, exercise, mock(VariantGenerationRequestDTO.class));
        jobService.updatePhase(job.getJobId(), VariantJobPhase.VERIFYING);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicBoolean finalizing = new AtomicBoolean();
        AtomicBoolean cancelAccepted = new AtomicBoolean();
        try {
            executor.submit(() -> {
                ready.countDown();
                awaitUninterruptibly(go);
                finalizing.set(jobService.enterFinalizingUnlessCancelled(job.getJobId()));
            });
            executor.submit(() -> {
                ready.countDown();
                awaitUninterruptibly(go);
                try {
                    jobService.requestCancel(job.getJobId(), "instructor1");
                    cancelAccepted.set(true);
                }
                catch (ConflictException expectedWhenFinalizingWon) {
                    cancelAccepted.set(false);
                }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(finalizing.get() && cancelAccepted.get()).as("an accepted cancellation must not be followed by FINALIZING").isFalse();
        if (cancelAccepted.get()) {
            assertThat(jobService.isCancelRequested(job.getJobId())).isTrue();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
