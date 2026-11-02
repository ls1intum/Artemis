package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationFileUpdate;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class GenerationJobReplayBoundaryTest {

    private final LocalDataProviderService provider = new LocalDataProviderService();

    private final DistributedMap<String, Boolean> cancellations = provider.getExpiringMap("cancellations", Duration.ofHours(4));

    private final DistributedMap<String, GenerationJobService.JobInfo> jobs = provider.getMap("hyperion-exercise-generation-jobs");

    private GenerationJobReplayStore store;

    private User owner;

    private User other;

    private ProgrammingExercise exercise;

    private GenerationJobService.JobInfo job;

    @BeforeEach
    void setup() {
        store = new GenerationJobReplayStore(provider, Duration.ofHours(4), Duration.ofMinutes(30), cancellations);
        owner = new User();
        owner.setLogin("owner");
        other = new User();
        other.setLogin("other");
        exercise = new ProgrammingExercise();
        exercise.setId(7L);
        job = new GenerationJobService.JobInfo("job", "owner", 7L, Instant.now(), null, "node", null, true, null, GenerationMode.GENERATE, "Exercise", 1L);
    }

    private void start() {
        jobs.put("7", job);
        store.initializeStart(7L, "job", "owner", GenerationMode.GENERATE, "small");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "PT0S", "PT-1S" })
    void terminalReplayRequiresPositiveRetention(String duration) {
        assertThatThrownBy(() -> new GenerationJobReplayStore(provider, duration == null ? null : Duration.parse(duration), Duration.ofMinutes(30), cancellations))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("terminal-replay-ttl must be positive");
    }

    @Test
    void missingOrSupersededEvidenceCannotBeMutatedByOldWorker() {
        assertThat(store.recordEvent(7L, "old", ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, "ignored"), false)).isFalse();
        assertThat(store.recordSpecDocument(7L, "old", "ignored")).isFalse();
        assertThat(store.recordFileChange(7L, "old", ExerciseGenerationFileChangeDTO.of("solution/X.java", "write", 1))).isFalse();
        assertThat(store.cancellationReplayState(job)).isNull();
        assertThat(store.appendCancellation(job, "ignored")).isNull();
        assertThat(store.getStatus(owner, exercise)).isEmpty();
        start();
        assertThat(store.recordSpecDocument(7L, "old", "ignored")).isFalse();
        assertThat(store.recordFileChange(7L, "old", ExerciseGenerationFileChangeDTO.of("solution/X.java", "write", 1))).isFalse();
        assertThat(store.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.events()).isEmpty();
            assertThat(status.fileChanges()).isEmpty();
            assertThat(status.specDocument()).isNull();
        });
    }

    @Test
    void activeJobWithoutMatchingTranscriptStillReportsOwnerAndSanitizesForeignStatus() {
        jobs.put("7", job);
        assertThat(store.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isTrue();
            assertThat(status.ownedByCaller()).isTrue();
            assertThat(status.cancellable()).isTrue();
            assertThat(status.mode()).isNull();
            assertThat(status.accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
        });
        assertThat(store.getStatus(other, exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isTrue();
            assertThat(status.ownedByCaller()).isFalse();
            assertThat(status.cancellable()).isFalse();
            assertThat(status.events()).isEmpty();
            assertThat(status.usage()).isNull();
        });
        // Undo reservations share the slot but are not advertised as generation runs.
        jobs.put("7", new GenerationJobService.JobInfo(GenerationJobService.REVERT_JOB_PREFIX + "undo", "owner", 7L, Instant.now(), null, "node", null, false, null));
        assertThat(store.getStatus(owner, exercise)).isEmpty();
    }

    @Test
    void cancellationFreezesReplayAndNonOwnersSeeOnlyTerminalOutcome() {
        start();
        store.recordEvent(7L, "job", ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, "private progress"), false);
        assertThat(store.getStatus(other, exercise)).hasValueSatisfying(status -> assertThat(status.events()).isEmpty());
        assertThat(store.appendCancellation(job, "Cancelled by owner").type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
        assertThat(store.appendCancellation(job, "duplicate")).isNull();
        assertThat(store.cancellationReplayState(job).done()).isTrue();
        assertThat(store.recordSpecDocument(7L, "job", "too late")).isFalse();
        assertThat(store.recordEvent(7L, "job", ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, "too late"), false)).isFalse();
        assertThat(store.getStatus(other, exercise)).hasValueSatisfying(status -> {
            assertThat(status.events()).singleElement().satisfies(event -> assertThat(event.message()).isEqualTo("Cancelled by owner"));
            assertThat(status.running()).isFalse();
            assertThat(status.effortProfile()).isNull();
            assertThat(status.accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
        });
        jobs.remove("7");
        assertThat(store.getStatus(other, exercise)).hasValueSatisfying(status -> assertThat(status.events()).hasSize(1));
    }

    @Test
    void fileReplayDeduplicatesPathsBoundsOldChangesAndPublishesApprovedSpec() {
        start();
        for (int index = 0; index < 302; index++) {
            store.recordFileChange(7L, "job", ExerciseGenerationFileChangeDTO.of("solution/File" + index + ".java", "write", index));
        }
        store.recordFileUpdate(7L, "job", new GenerationFileUpdate(ExerciseGenerationFileChangeDTO.of("SPEC.md", "write", 303), "Approved design"));
        store.recordFileChange(7L, "job", ExerciseGenerationFileChangeDTO.of("solution/File301.java", "edit", 304));
        assertThat(store.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.fileChanges()).hasSize(300);
            assertThat(status.fileChanges().getFirst().path()).isEqualTo("solution/File3.java");
            assertThat(status.fileChanges()).filteredOn(change -> change.path().equals("solution/File301.java")).singleElement()
                    .satisfies(change -> assertThat(change.action()).isEqualTo("edit"));
            assertThat(status.specDocument()).isEqualTo("Approved design");
            assertThat(status.artifactsRetained()).isTrue();
        });
        assertThat(store.getRetainedArtifacts(other, exercise)).isEmpty();
        assertThat(store.getRetainedArtifacts(owner, exercise)).hasValueSatisfying(artifacts -> assertThat(artifacts.specDocument()).isEqualTo("Approved design"));
        cancellations.put("job", true);
        assertThat(store.recordFileChange(7L, "job", ExerciseGenerationFileChangeDTO.of("solution/Blocked.java", "write", 305))).isFalse();
    }

    @Test
    void workerExitWithoutTerminalOutcomePermanentlyMarksAccountingIncomplete() {
        start();
        assertThatThrownBy(() -> store.recordToolCalls("job", -1)).isInstanceOf(IllegalArgumentException.class).hasMessage("Tool call count cannot be negative");
        store.recordAgentTurn("job");
        store.recordAttempt("job");
        store.sealUsageOnWorkerExit(7L, "job");
        assertThat(store.usageSnapshot("job").accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
        store.sealUsage("job");
        assertThat(store.usageSnapshot("job").accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
        store.discardRetainedRun(7L, "job");
        assertThat(store.getStatus(owner, exercise)).hasValueSatisfying(status -> assertThat(status.events()).isEmpty());
        assertThat(store.usageSnapshot("job").usage()).isNull();
        store.sealUsage("missing");
        assertThat(store.usageSnapshot("missing").accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void usageStorageFailuresAreReportedAsIncompleteAndNeverBreakTerminalHandling() {
        LocalDataProviderService failingProvider = spy(new LocalDataProviderService());
        DistributedMap<String, Object> usage = mock(DistributedMap.class);
        doReturn(usage).when(failingProvider).getExpiringMap(eq("hyperion-exercise-generation-usage"), any());
        var failingStore = new GenerationJobReplayStore(failingProvider, Duration.ofHours(4), Duration.ofMinutes(30), cancellations);
        doThrow(new IllegalStateException("lock unavailable")).when(usage).lock("job");
        assertThatCode(() -> failingStore.recordToolCalls("job", 1)).doesNotThrowAnyException();
        assertThatCode(() -> failingStore.sealUsage("job")).doesNotThrowAnyException();
        assertThat(failingStore.usageSnapshot("job").accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
        doThrow(new IllegalStateException("read unavailable")).when(usage).get("job");
        assertThat(failingStore.usageSnapshot("job").usage()).isNull();
    }
}
