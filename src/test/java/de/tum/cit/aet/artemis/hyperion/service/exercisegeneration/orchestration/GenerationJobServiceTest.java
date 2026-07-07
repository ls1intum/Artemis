package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileSnapshotDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Unit test for {@link GenerationJobService}'s single-flight, transcript-cap, privacy-ownership and cancel-hook invariants against a real isolated embedded Hazelcast
 * instance, so it also exercises the same {@code Serializable} default serialization the distributed map uses in production.
 */
class GenerationJobServiceTest {

    private HazelcastInstance hazelcastInstance;

    private GenerationJobService jobService;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.setClusterName("hyperion-job-service-test-" + System.nanoTime());
        // Fully isolate: nothing shall ever join this instance.
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);

        // No-op publisher: the test needs only the slot/transcript side effects, not the run. Token usage is exercised elsewhere, so a mock sink source suffices here.
        jobService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class));
        jobService.init();
    }

    @AfterEach
    void tearDown() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
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

    private static ExerciseGenerationEventDTO progress(String message) {
        return ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, message);
    }

    @Test
    void startJob_secondConcurrentStartForSameExercise_throwsConflict() {
        ProgrammingExercise exercise = exercise(42L);
        User owner = user("owner");

        assertThat(jobService.startJob(owner, exercise, "do it", GenerationMode.GENERATE)).isNotBlank();
        // Single-flight: a second start while the first slot is still claimed must be rejected.
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.startJob(owner, exercise, "again", GenerationMode.GENERATE));
    }

    @Test
    void recordEvent_beyondCap_keepsStartedHeadAndDropsIndexOne_preservingOrder() {
        ProgrammingExercise exercise = exercise(7L);
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise, "go", GenerationMode.GENERATE);

        jobService.recordEvent(7L, jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "STARTED-HEAD"), false);
        int overflow = 600;
        for (int i = 0; i < overflow; i++) {
            jobService.recordEvent(7L, jobId, progress("p" + i), false);
        }

        List<ExerciseGenerationEventDTO> events = jobService.getStatus(owner, exercise).orElseThrow().events();

        assertThat(events).hasSize(500);
        // The STARTED head[0] is preserved (the mutation remove(1)->remove(0) would drop it).
        assertThat(events.getFirst().type()).isEqualTo(ExerciseGenerationEventDTO.Type.STARTED);
        assertThat(events.getFirst().message()).isEqualTo("STARTED-HEAD");
        // The oldest PROGRESS lines were dropped from the front of the remainder; the survivors are the most recent 499, still in order.
        assertThat(events.get(1).message()).isEqualTo("p" + (overflow - 499));
        assertThat(events.getLast().message()).isEqualTo("p" + (overflow - 1));
    }

    @Test
    void getStatus_carriesExplicitMode_soReconnectRestoresAdaptAffordances() {
        ProgrammingExercise exercise = exercise(31L);
        User owner = user("owner");
        jobService.startJob(owner, exercise, "fix it", GenerationMode.ADAPT);

        // A reconnecting client must recover the run intent from the status alone (it drives the header label and the revert button).
        assertThat(jobService.getStatus(owner, exercise).orElseThrow().mode()).isEqualTo(GenerationMode.ADAPT);
    }

    @Test
    void getStatus_forDifferentUser_returnsEmpty_privacyBoundary() {
        ProgrammingExercise exercise = exercise(99L);
        jobService.startJob(user("instructorA"), exercise, "go", GenerationMode.GENERATE);

        assertThat(jobService.getStatus(user("instructorA"), exercise)).isPresent();
        // A different instructor must NOT see another instructor's transcript (privacy boundary).
        assertThat(jobService.getStatus(user("instructorB"), exercise)).isEmpty();
    }

    @Test
    void requestCancellation_runsCancelHookExactlyOnce_thenRemovesIt() {
        String jobId = jobService.startJob(user("owner"), exercise(11L), "go", GenerationMode.GENERATE);
        AtomicInteger hookRuns = new AtomicInteger(0);
        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);

        assertThat(jobService.requestCancellation(11L, jobId, user("owner"))).isTrue();
        assertThat(hookRuns.get()).isEqualTo(1);
        assertThat(jobService.isCancelled(jobId)).isTrue();

        // A second cancellation for the same (still-claimed) job must not run the hook again — it was removed after the first run.
        assertThat(jobService.requestCancellation(11L, jobId, user("owner"))).isTrue();
        assertThat(hookRuns.get()).isEqualTo(1);
    }

    @Test
    void requestCancellation_byANonOwner_isRefused_andDoesNotCancel() {
        String jobId = jobService.startJob(user("owner"), exercise(12L), "go", GenerationMode.GENERATE);
        AtomicInteger hookRuns = new AtomicInteger(0);
        jobService.registerCancelHook(jobId, hookRuns::incrementAndGet);

        // A different editor of the same course must NOT be able to cancel the owner's run by the (observable) jobId — symmetric to the getStatus owner boundary.
        assertThat(jobService.requestCancellation(12L, jobId, user("notOwner"))).isFalse();
        assertThat(hookRuns.get()).isZero();
        assertThat(jobService.isCancelled(jobId)).isFalse();
        // The owner can still cancel.
        assertThat(jobService.requestCancellation(12L, jobId, user("owner"))).isTrue();
        assertThat(jobService.isCancelled(jobId)).isTrue();
    }

    @Test
    void getStatus_emptyWhenNothingRetained() {
        assertThat(jobService.getStatus(user("owner"), exercise(123L))).isEmpty();
    }

    // --- File-snapshot store: per-file keying so a write is O(1), never a re-serialization of the whole retained set
    // --------------------------------------------------------------

    private static ExerciseGenerationFileSnapshotDTO snapshot(String path, String content) {
        return ExerciseGenerationFileSnapshotDTO.of(path, ExerciseGenerationFileSnapshotDTO.ACTION_CREATE, content, 1);
    }

    private IMap<String, ExerciseGenerationFileSnapshotDTO> perFileSnapshotMap() {
        return hazelcastInstance.getMap(GenerationJobService.SNAPSHOT_MAP_NAME);
    }

    /** The per-file store as a plain {@link Map}, so {@code assertThat(...)} resolves the Map overload (an {@code IMap} matches both the Map and Iterable AssertJ overloads). */
    private Map<String, ExerciseGenerationFileSnapshotDTO> perFileEntries() {
        return perFileSnapshotMap();
    }

    /**
     * Each write targets exactly ONE distributed-map key (that file's own key), never re-serializing the other retained files: 150 distinct-file writes leave 150 independent
     * per-file entries, and a repeat write to one file updates only that file's key. This is the structural fix for the former O(n^2) whole-set rewrite.
     */
    @Test
    void recordSnapshot_keysEachFileSeparately_soAWriteTouchesOneKeyNotTheWholeSet() {
        long exerciseId = 501L;
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise(exerciseId), "go", GenerationMode.GENERATE);

        List<String> addedKeys = new CopyOnWriteArrayList<>();
        List<String> updatedKeys = new CopyOnWriteArrayList<>();
        IMap<String, ExerciseGenerationFileSnapshotDTO> perFileMap = perFileSnapshotMap();
        perFileMap.addEntryListener((EntryAddedListener<String, ExerciseGenerationFileSnapshotDTO>) event -> addedKeys.add(event.getKey()), false);
        perFileMap.addEntryListener((EntryUpdatedListener<String, ExerciseGenerationFileSnapshotDTO>) event -> updatedKeys.add(event.getKey()), false);

        int fileCount = 150;
        for (int i = 0; i < fileCount; i++) {
            jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/File" + i + ".java", "body " + i));
        }
        // Each distinct file occupies its own entry — the whole set is NOT a single re-serialized value.
        assertThat(perFileMap.size()).isEqualTo(fileCount);

        // Every write produced exactly one ADD event, each on that file's own distinct key (proving a write touches ONE key, not the whole set).
        String[] expectedKeys = new String[fileCount];
        for (int i = 0; i < fileCount; i++) {
            expectedKeys[i] = GenerationJobService.fileKey(exerciseId, "solution/File" + i + ".java");
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(addedKeys).hasSize(fileCount));
        assertThat(addedKeys).doesNotHaveDuplicates().containsExactlyInAnyOrder(expectedKeys);

        // Re-writing one existing file updates ONLY that file's key (latest-per-file), leaving every other key untouched — again a single-key write.
        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/File0.java", "rewritten"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(updatedKeys).containsExactly(GenerationJobService.fileKey(exerciseId, "solution/File0.java")));
        assertThat(perFileMap.size()).as("a repeat write does not add a new key").isEqualTo(fileCount);

        // The replay view carries the latest content per file in write order.
        List<ExerciseGenerationFileSnapshotDTO> replay = jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots();
        assertThat(replay).hasSize(fileCount);
        assertThat(replay.getFirst().path()).isEqualTo("solution/File0.java");
        assertThat(replay.getFirst().content()).isEqualTo("rewritten");
        assertThat(replay).extracting(ExerciseGenerationFileSnapshotDTO::path).endsWith("solution/File" + (fileCount - 1) + ".java");
    }

    /** Beyond the 300-file cap the eldest file is evicted (its per-file key removed too) and the replay stays bounded and in write order. */
    @Test
    void recordSnapshot_beyondCap_evictsEldestKeepingWriteOrderAndBoundedKeys() {
        long exerciseId = 502L;
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise(exerciseId), "go", GenerationMode.GENERATE);

        int total = GenerationJobService.MAX_RETAINED_SNAPSHOT_FILES + 50;
        for (int i = 0; i < total; i++) {
            jobService.recordSnapshot(exerciseId, jobId, snapshot("tests/File" + i + ".java", "body " + i));
        }

        // The per-file store is bounded to the cap; the eldest 50 files (and their keys) were evicted.
        assertThat(perFileSnapshotMap().size()).isEqualTo(GenerationJobService.MAX_RETAINED_SNAPSHOT_FILES);
        assertThat(perFileEntries()).doesNotContainKey(GenerationJobService.fileKey(exerciseId, "tests/File0.java"));

        List<ExerciseGenerationFileSnapshotDTO> replay = jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots();
        assertThat(replay).hasSize(GenerationJobService.MAX_RETAINED_SNAPSHOT_FILES);
        // The survivors are the most-recent 300 in write order (files 50..349).
        assertThat(replay.getFirst().path()).isEqualTo("tests/File50.java");
        assertThat(replay.getLast().path()).isEqualTo("tests/File" + (total - 1) + ".java");
    }

    /** clearJob deletes every retained per-file key and the index; a later status carries no file snapshots (the live preview is a during-run affordance). */
    @Test
    void clearJob_deletesAllPerFileSnapshotKeysAndIndex() {
        long exerciseId = 503L;
        User owner = user("owner");
        String jobId = jobService.startJob(owner, exercise(exerciseId), "go", GenerationMode.GENERATE);
        jobService.recordSnapshot(exerciseId, jobId, snapshot("solution/A.java", "a"));
        jobService.recordSnapshot(exerciseId, jobId, snapshot("template/B.java", "b"));
        assertThat(perFileSnapshotMap().size()).isEqualTo(2);

        jobService.clearJob(exerciseId, jobId);

        // No orphaned per-file keys survive the run's teardown (per-file keys multiply per run, so they must be actively cleared, not left to the TTL).
        assertThat(perFileEntries()).doesNotContainKey(GenerationJobService.fileKey(exerciseId, "solution/A.java"))
                .doesNotContainKey(GenerationJobService.fileKey(exerciseId, "template/B.java"));
        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots()).isEmpty();
    }

    /**
     * A new run for the same exercise wipes a previous run's lingering retained snapshots (a crashed run whose slot TTL'd out but whose index/keys lingered), so it never inherits
     * stale files. Simulated by seeding a lingering index+key directly, since the single-flight guard forbids starting a second run while the first slot is still claimed.
     */
    @Test
    void startJob_forANewRun_clearsAPreviousRunsLingeringRetainedSnapshots() {
        long exerciseId = 504L;
        User owner = user("owner");
        IMap<String, GenerationJobService.JobFileSnapshotIndex> indexMap = hazelcastInstance.getMap(GenerationJobService.SNAPSHOT_INDEX_MAP_NAME);
        indexMap.set(String.valueOf(exerciseId), new GenerationJobService.JobFileSnapshotIndex("crashed-job", "owner", List.of("solution/Stale.java")));
        perFileSnapshotMap().set(GenerationJobService.fileKey(exerciseId, "solution/Stale.java"), snapshot("solution/Stale.java", "old"));

        String secondJob = jobService.startJob(owner, exercise(exerciseId), "again", GenerationMode.GENERATE);
        assertThat(perFileEntries()).as("the new run starts from a clean snapshot store").doesNotContainKey(GenerationJobService.fileKey(exerciseId, "solution/Stale.java"));
        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots()).isEmpty();
        // The new run's own writes are retained under its job.
        jobService.recordSnapshot(exerciseId, secondJob, snapshot("solution/Fresh.java", "new"));
        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots()).extracting(ExerciseGenerationFileSnapshotDTO::path)
                .containsExactly("solution/Fresh.java");
    }

    /** A snapshot recorded for a stale/superseded job id is dropped (its index guard rejects it), leaving no per-file key. */
    @Test
    void recordSnapshot_forAStaleJobId_isDropped() {
        long exerciseId = 505L;
        User owner = user("owner");
        jobService.startJob(owner, exercise(exerciseId), "go", GenerationMode.GENERATE);

        jobService.recordSnapshot(exerciseId, "some-older-job", snapshot("solution/Ignored.java", "x"));

        assertThat(perFileEntries()).doesNotContainKey(GenerationJobService.fileKey(exerciseId, "solution/Ignored.java"));
        assertThat(jobService.getStatus(owner, exercise(exerciseId)).orElseThrow().fileSnapshots()).isEmpty();
    }

    @Test
    void startJob_whenExecutorRejectsPublish_releasesSlotAndReportsBusy_notWedged() {
        // The generation executor uses AbortPolicy, so on saturation publishEvent throws TaskRejectedException (a RejectedExecutionException) synchronously back through startJob.
        // Reproduce that with a publisher that rejects while `reject` is set, then reuse the same exercise to prove the single-flight slot was rolled back (not wedged for the
        // TTL).
        AtomicBoolean reject = new AtomicBoolean(true);
        AtomicInteger publishAttempts = new AtomicInteger(0);
        ApplicationEventPublisher rejectingPublisher = event -> {
            publishAttempts.incrementAndGet();
            if (reject.get()) {
                throw new TaskRejectedException("hyperionGenerationExecutor is saturated");
            }
        };
        GenerationJobService service = new GenerationJobService(hazelcastInstance, rejectingPublisher, mock(LLMTokenUsageService.class));
        service.init();

        ProgrammingExercise exercise = exercise(77L);
        User owner = user("owner");

        // (a) The rejection surfaces as a distinct busy/capacity error (not the single-flight "already running" conflict), so the REST layer returns a clear failure.
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> service.startJob(owner, exercise, "go", GenerationMode.GENERATE))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("exerciseGenerationCapacityExceeded")).withMessageContaining("busy");
        assertThat(publishAttempts.get()).isEqualTo(1);

        // (c) No stale transcript/snapshot leaked from the failed claim — the owner sees nothing retained.
        assertThat(service.getStatus(owner, exercise)).isEmpty();

        // (b) The slot was released: a subsequent start for the SAME exercise succeeds instead of hitting the single-flight guard (which is what a wedged, un-rolled-back slot
        // would do).
        reject.set(false);
        String jobId = service.startJob(owner, exercise, "retry", GenerationMode.GENERATE);
        assertThat(jobId).isNotBlank();
        assertThat(service.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.jobId()).isEqualTo(jobId);
            assertThat(status.running()).isTrue();
        });
    }
}
