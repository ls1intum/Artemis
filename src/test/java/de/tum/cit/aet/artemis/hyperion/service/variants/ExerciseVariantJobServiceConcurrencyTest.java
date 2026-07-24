package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;

/**
 * Regression test for a lost-update race: {@code heartbeat()} used to do its own independent get-modify-put on
 * the Hazelcast job map, completely bypassing {@code mutate()}'s locking — a heartbeat landing between
 * {@code requestCancel()}'s read and write could silently revert the cancel flag back to false, since whichever
 * writer's {@code put()} landed last would overwrite the other's stale-copy change. Both now share one per-key
 * {@code IMap} lock, making the two mutually exclusive. Uses a real embedded Hazelcast instance (not a mock) —
 * a mocked {@code IMap} would not exercise real per-key locking semantics, and this bug only reproduces under
 * genuine concurrent access to the same distributed map entry.
 */
class ExerciseVariantJobServiceConcurrencyTest {

    private HazelcastInstance hazelcastInstance;

    private ExerciseVariantJobService jobService;

    @BeforeEach
    void setUp() {
        hazelcastInstance = Hazelcast.newHazelcastInstance();
        jobService = new ExerciseVariantJobService(hazelcastInstance, mock(HyperionWebsocketService.class));
        jobService.init();
    }

    @AfterEach
    void tearDown() {
        hazelcastInstance.shutdown();
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

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
