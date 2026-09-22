package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationIncompleteException;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationPersistenceService;
import de.tum.cit.aet.artemis.hyperion.test_repository.AuthoringRunTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class GenerationRunJournalServiceTest {

    private final AuthoringRunTestRepository runs = mock(AuthoringRunTestRepository.class);

    private final ExerciseVersionService versions = mock(ExerciseVersionService.class);

    private final ExerciseVersionTestRepository versionRepository = mock(ExerciseVersionTestRepository.class);

    private final GenerationRunJournalService journal = new GenerationRunJournalService(runs, versions, versionRepository);

    private final ProgrammingExercise exercise = new ProgrammingExercise();

    private final User user = new User();

    private final Map<RepositoryType, String> heads = Map.of(RepositoryType.TEMPLATE, "template", RepositoryType.SOLUTION, "solution", RepositoryType.TESTS, "tests");

    @BeforeEach
    void setup() {
        exercise.setId(12L);
        user.setId(7L);
        var run = new AuthoringRun();
        run.setExerciseId(12L);
        run.setOwnerId(7L);
        when(runs.findByJobId("job")).thenReturn(Optional.of(run));
    }

    @Test
    void recordsAdmittedIdentityBeforeAsynchronousExecution() {
        journal.started(new GenerationStartedEvent("job", user, exercise, "brief", GenerationMode.ADAPT));
        var captured = ArgumentCaptor.forClass(AuthoringRun.class);
        verify(runs).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getJobId()).isEqualTo("job");
        assertThat(captured.getValue().getExerciseId()).isEqualTo(12L);
        assertThat(captured.getValue().getSourceExerciseId()).isEqualTo(12L);
        assertThat(captured.getValue().getOwnerId()).isEqualTo(7L);
        assertThat(captured.getValue().getKind()).isEqualTo(AuthoringRun.Kind.ADAPT);
    }

    @Test
    void refusesToBeginWhenThePriorVersionCannotBeCaptured() {
        when(versions.createExerciseVersionOrThrow(exercise, user, heads)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> journal.beforeMutation("job", exercise, user, heads, "teaching")).isInstanceOf(IllegalStateException.class);
        verify(runs, never()).linkBeforeVersion(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any());
    }

    @Test
    void refusesToBeginWhenTheVersionLinkCannotBeStored() {
        when(versions.createExerciseVersionOrThrow(exercise, user, heads)).thenReturn(81L);
        assertThatThrownBy(() -> journal.beforeMutation("job", exercise, user, heads, "teaching")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer eligible");
        verify(runs).linkBeforeVersion(eq("job"), eq(81L), eq("teaching"), any());
    }

    @Test
    void reusesAnIdenticalCanonicalVersionWithoutDroppingTheBeforeLink() {
        when(versions.createExerciseVersionOrThrow(exercise, user, heads)).thenReturn(null);
        var existing = new ExerciseVersion();
        existing.setId(81L);
        when(versionRepository.findTopByExerciseIdOrderByCreatedDateDesc(12L)).thenReturn(Optional.of(existing));
        when(runs.linkBeforeVersion(eq("job"), eq(81L), eq("teaching"), any())).thenReturn(1);
        journal.beforeMutation("job", exercise, user, heads, "teaching");
        verify(runs).linkBeforeVersion(eq("job"), eq(81L), eq("teaching"), any());
    }

    @Test
    void rejectsAnUnrelatedDestinationBeforeSnapshottingAnything() {
        exercise.setId(99L);
        assertThatThrownBy(() -> journal.beforeMutation("job", exercise, user, heads, "teaching")).isInstanceOf(IllegalStateException.class).hasMessageContaining("does not match");
        verify(versions, never()).createExerciseVersionOrThrow(any(), any(), any());
    }

    @Test
    void aMissingJournalAfterPersistenceIsPartialNotAnUnchangedFailure() {
        when(runs.findByJobId("job")).thenReturn(Optional.empty());
        var persisted = mock(GenerationPersistenceService.PersistResult.class);
        when(persisted.postPersistHeads()).thenReturn(heads);
        assertThatThrownBy(() -> journal.afterMutation("job", exercise, user, persisted)).isInstanceOfSatisfying(GenerationIncompleteException.class,
                exception -> assertThat(exception.liveExerciseChanged()).isTrue());
    }

    @Test
    void finalVersionIsCapturedAfterPlacementRatherThanReusingTheEarlierPersistenceVersion() throws Exception {
        var run = mock(AuthoringRun.class);
        when(run.getExerciseId()).thenReturn(12L);
        when(run.getMutationStartedAt()).thenReturn(java.time.Instant.now());
        when(runs.findByJobId("job")).thenReturn(Optional.of(run));
        var persisted = mock(GenerationPersistenceService.PersistResult.class);
        when(run.getBeforeVersionId()).thenReturn(81L);
        var previous = new ExerciseVersion();
        previous.setExerciseSnapshot(de.tum.cit.aet.artemis.core.util.JsonObjectMapper.get().readValue("""
                {"id":12,"programmingData":{
                    "templateParticipation":{"id":1,"commitId":"template"},
                    "solutionParticipation":{"id":2,"commitId":"solution"},"testsCommitId":"tests"}}
                """, de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO.class));
        when(versionRepository.findById(81L)).thenReturn(Optional.of(previous));
        when(persisted.postPersistHeads()).thenReturn(Map.of(RepositoryType.SOLUTION, "saved-solution"));
        var finalHeads = Map.of(RepositoryType.TEMPLATE, "template", RepositoryType.SOLUTION, "saved-solution", RepositoryType.TESTS, "tests");
        when(versions.createExerciseVersionOrThrow(exercise, user, finalHeads)).thenReturn(92L);
        when(runs.linkAfterVersion("job", 92L)).thenReturn(1);

        assertThat(journal.afterMutation("job", exercise, user, persisted)).isEqualTo(92L);

        verify(versions).createExerciseVersionOrThrow(exercise, user, finalHeads);
        verify(runs).linkAfterVersion("job", 92L);
    }

    @Test
    void placementReusesTheOriginalBeforeVersionWithoutSnapshottingTheAlreadyChangedArtifacts() {
        var run = mock(AuthoringRun.class);
        when(run.getExerciseId()).thenReturn(12L);
        when(run.getOwnerId()).thenReturn(7L);
        when(run.getMutationStartedAt()).thenReturn(java.time.Instant.now());
        when(run.getBeforeVersionId()).thenReturn(81L);
        when(run.getRepositoryBranch()).thenReturn("teaching");
        when(runs.findByJobId("job")).thenReturn(Optional.of(run));

        journal.beforeMutation("job", exercise, user, heads, "teaching");

        verify(versions, never()).createExerciseVersionOrThrow(any(), any(), any());
        when(run.getBeforeVersionId()).thenReturn(null);
        assertThatThrownBy(() -> journal.beforeMutation("job", exercise, user, heads, "teaching")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordsOnlyTerminalOutcomes() {
        journal.completed("job", ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "started"));
        verify(runs, never()).complete(any(), any(), any(), any());
        var failed = ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "failed");
        journal.completed("job", failed);
        verify(runs).complete("job", AuthoringRun.Status.ERROR, failed.timestamp(), null);
    }
}
