package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerationJobReplayStoreTest {

    private HazelcastInstance hazelcastInstance;

    private GenerationJobReplayStore replayStore;

    @BeforeAll
    void startHazelcast() {
        Config config = new Config();
        config.setClusterName("hyperion-job-replay-store-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @BeforeEach
    void setUp() {
        hazelcastInstance.getDistributedObjects().forEach(distributedObject -> distributedObject.destroy());
        replayStore = new GenerationJobReplayStore(hazelcastInstance);
    }

    @AfterAll
    void stopHazelcast() {
        hazelcastInstance.shutdown();
    }

    @Test
    void initializeStart_whenTranscriptInitializationFails_keepsReplayEmpty() {
        long exerciseId = 82L;
        String key = String.valueOf(exerciseId);
        IMap<String, GenerationJobService.JobTranscript> failingTranscriptMap = spy(transcriptMap());
        doThrow(new IllegalStateException("transcript initialization failed")).when(failingTranscriptMap).set(eq(key), any(GenerationJobService.JobTranscript.class));
        ReflectionTestUtils.setField(replayStore, "transcriptMap", failingTranscriptMap);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> replayStore.initializeStart(exerciseId, "failed", "owner", GenerationMode.GENERATE))
                .withMessageContaining("transcript initialization failed");

        assertThat(transcriptMap().get(key)).isNull();
        assertThat(fileChangeMap().get(key)).isNull();
    }

    @Test
    void initializeStart_whenFileChangeInitializationFails_restoresPreviousReplay() {
        long exerciseId = 83L;
        String key = String.valueOf(exerciseId);
        GenerationJobService.JobTranscript previousTranscript = transcript("previous", exerciseId, GenerationMode.ADAPT, true);
        GenerationJobService.JobFileChangeIndex previousIndex = fileChangeIndex("previous", "solution/Previous.java");
        transcriptMap().set(key, previousTranscript);
        fileChangeMap().set(key, previousIndex);
        IMap<String, GenerationJobService.JobFileChangeIndex> failingFileChangeMap = spy(fileChangeMap());
        doThrow(new IllegalStateException("file-change initialization failed")).when(failingFileChangeMap).set(eq(key), any(GenerationJobService.JobFileChangeIndex.class));
        ReflectionTestUtils.setField(replayStore, "fileChangeMap", failingFileChangeMap);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> replayStore.initializeStart(exerciseId, "failed", "owner", GenerationMode.GENERATE))
                .withMessageContaining("file-change initialization failed");

        assertThat(transcriptMap().get(key)).isEqualTo(previousTranscript);
        assertThat(fileChangeMap().get(key)).isEqualTo(previousIndex);
    }

    @Test
    void restoreUnpublishedStart_doesNotOverwriteNewerReplayState() {
        long exerciseId = 80L;
        String key = String.valueOf(exerciseId);
        GenerationJobService.JobTranscript failedTranscript = transcript("failed", exerciseId, GenerationMode.GENERATE, false);
        GenerationJobService.JobFileChangeIndex failedIndex = fileChangeIndex("failed", "solution/Failed.java");
        GenerationJobService.JobTranscript previousTranscript = transcript("previous", exerciseId, GenerationMode.GENERATE, true);
        GenerationJobService.JobFileChangeIndex previousIndex = fileChangeIndex("previous", "solution/Previous.java");
        GenerationJobService.JobTranscript newerTranscript = transcript("newer", exerciseId, GenerationMode.ADAPT, false);
        GenerationJobService.JobFileChangeIndex newerIndex = fileChangeIndex("newer", "solution/Newer.java");
        transcriptMap().set(key, newerTranscript);
        fileChangeMap().set(key, newerIndex);
        GenerationJobReplayStore.StartedReplay failedReplay = new GenerationJobReplayStore.StartedReplay(failedTranscript, failedIndex, previousTranscript, previousIndex);

        replayStore.restoreUnpublishedStart(exerciseId, failedReplay);

        assertThat(transcriptMap().get(key)).isEqualTo(newerTranscript);
        assertThat(fileChangeMap().get(key)).isEqualTo(newerIndex);
    }

    @Test
    void getStatus_doesNotObserveUnpublishedReplayHalfwayThroughRestore() throws Exception {
        long exerciseId = 81L;
        String key = String.valueOf(exerciseId);
        User owner = user("owner");
        ProgrammingExercise exercise = exercise(exerciseId);
        GenerationJobService.JobTranscript failedTranscript = transcript("failed", exerciseId, GenerationMode.GENERATE, false);
        GenerationJobService.JobFileChangeIndex failedIndex = fileChangeIndex("failed", null);
        GenerationJobService.JobTranscript previousTranscript = transcript("previous", exerciseId, GenerationMode.ADAPT, true);
        GenerationJobService.JobFileChangeIndex previousIndex = fileChangeIndex("previous", null);
        transcriptMap().set(key, failedTranscript);
        fileChangeMap().set(key, failedIndex);
        GenerationJobReplayStore.StartedReplay failedReplay = new GenerationJobReplayStore.StartedReplay(failedTranscript, failedIndex, previousTranscript, previousIndex);
        IMap<String, GenerationJobService.JobInfo> originalJobMap = jobMap();
        IMap<String, GenerationJobService.JobInfo> observedJobMap = spy(originalJobMap);
        CountDownLatch lockAttempts = new CountDownLatch(2);
        doAnswer(invocation -> {
            lockAttempts.countDown();
            originalJobMap.lock(key);
            return null;
        }).when(observedJobMap).lock(key);
        ReflectionTestUtils.setField(replayStore, "jobMap", observedJobMap);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicBoolean transcriptLockHeld = new AtomicBoolean(true);

        transcriptMap().lock(key);
        try {
            Future<?> restore = executor.submit(() -> replayStore.restoreUnpublishedStart(exerciseId, failedReplay));
            Future<Optional<ExerciseGenerationStatusDTO>> status = executor.submit(() -> replayStore.getStatus(owner, exercise));
            assertThat(lockAttempts.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(restore.isDone()).isFalse();
            assertThat(status.isDone()).isFalse();

            transcriptMap().unlock(key);
            transcriptLockHeld.set(false);
            assertThat(restore.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(status.get(5, TimeUnit.SECONDS)).hasValueSatisfying(value -> assertThat(value.jobId()).isEqualTo("previous"));
        }
        finally {
            if (transcriptLockHeld.get()) {
                transcriptMap().unlock(key);
            }
            executor.shutdownNow();
        }
    }

    private static User user(String login) {
        User user = new User();
        user.setLogin(login);
        return user;
    }

    private static ProgrammingExercise exercise(long id) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }

    private static GenerationJobService.JobTranscript transcript(String jobId, long exerciseId, GenerationMode mode, boolean done) {
        return new GenerationJobService.JobTranscript(jobId, "owner", exerciseId, mode, List.of(), done, null);
    }

    private static GenerationJobService.JobFileChangeIndex fileChangeIndex(String jobId, String path) {
        List<ExerciseGenerationFileChangeDTO> changes = path == null ? List.of()
                : List.of(ExerciseGenerationFileChangeDTO.of(path, ExerciseGenerationFileChangeDTO.ACTION_WRITE, 1));
        return new GenerationJobService.JobFileChangeIndex(jobId, "owner", changes);
    }

    private IMap<String, GenerationJobService.JobInfo> jobMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-jobs");
    }

    private IMap<String, GenerationJobService.JobTranscript> transcriptMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-transcripts");
    }

    private IMap<String, GenerationJobService.JobFileChangeIndex> fileChangeMap() {
        return hazelcastInstance.getMap("hyperion-exercise-generation-file-changes");
    }
}
