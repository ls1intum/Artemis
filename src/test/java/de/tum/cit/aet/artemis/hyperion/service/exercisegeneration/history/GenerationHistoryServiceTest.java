package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseGenerationRevertService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class GenerationHistoryServiceTest {

    private final GenerationRunStoreService runs = mock(GenerationRunStoreService.class);

    private final ProgrammingExerciseTestRepository exercises = mock(ProgrammingExerciseTestRepository.class);

    private final AuthorizationCheckService authorization = mock(AuthorizationCheckService.class);

    private final GenerationJobService jobs = mock(GenerationJobService.class);

    private final ExerciseGenerationRevertService recovery = mock(ExerciseGenerationRevertService.class);

    private final GenerationHistoryService history = new GenerationHistoryService(runs, exercises, authorization, jobs, recovery);

    private final User user = new User();

    private final ProgrammingExercise exercise = new ProgrammingExercise();

    private final AuthoringRun run = mock(AuthoringRun.class);

    @BeforeEach
    void setup() {
        user.setId(7L);
        exercise.setId(12L);
        exercise.setTitle("Stack");
        var course = new Course();
        course.setId(8L);
        exercise.setCourse(course);
        when(run.getId()).thenReturn(41L);
        when(run.getJobId()).thenReturn("old-job");
        when(run.getOwnerId()).thenReturn(7L);
        when(run.getExerciseId()).thenReturn(12L);
        when(run.getKind()).thenReturn(AuthoringRun.Kind.ADAPT);
        when(run.getStatus()).thenReturn(AuthoringRun.Status.SAVED);
        when(run.getStartedAt()).thenReturn(Instant.parse("2030-01-01T12:00:00Z"));
        when(run.getFinishedAt()).thenReturn(Instant.parse("2030-01-01T12:15:00Z"));
        when(runs.findByJobId("old-job")).thenReturn(Optional.of(run));
        when(exercises.findWithEagerCourseAndExamById(12L)).thenReturn(Optional.of(exercise));
        when(authorization.isAtLeastEditorForExercise(exercise, user)).thenReturn(true);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    void pendingRestoreOverridesSavedOutcomeWithoutDiscardingRetainedAccounting(boolean retained) {
        when(run.getRestoreStartedAt()).thenReturn(Instant.parse("2030-01-02T12:00:00Z"));
        if (retained) {
            var replay = new ExerciseGenerationStatusDTO("old-job", false, GenerationMode.ADAPT, List.of(), List.of(), false).withUsage(null,
                    ExerciseGenerationAccountingState.COMPLETE);
            when(jobs.getStatus(user, exercise)).thenReturn(Optional.of(replay));
        }
        var status = history.status(12L, "old-job", user);
        assertThat(status.run().status()).isEqualTo(AuthoringRun.Status.PARTIAL);
        assertThat(status.events().getLast().completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
        assertThat(status.events().getLast().message()).contains("Restoration is incomplete");
        assertThat(status.accountingState()).isEqualTo(retained ? ExerciseGenerationAccountingState.COMPLETE : ExerciseGenerationAccountingState.INCOMPLETE);
    }

    @Test
    void canonicalLookupDoesNotReplaceAnOldRunWithTheLatestRunOnTheSameExercise() {
        var newer = mock(ExerciseGenerationStatusDTO.class);
        when(newer.jobId()).thenReturn("new-job");
        when(jobs.getStatus(user, exercise)).thenReturn(Optional.of(newer));
        var result = history.status(12L, "old-job", user);
        assertThat(result.jobId()).isEqualTo("old-job");
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
            assertThat(event.timestamp()).isEqualTo(run.getFinishedAt());
        });
        assertThat(result.usage()).isNull();
        assertThat(result.accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
        assertThat(result.running()).isFalse();
    }

    @Test
    void returnsExactRetainedReplayWhenAvailable() {
        var replay = mock(ExerciseGenerationStatusDTO.class);
        when(replay.jobId()).thenReturn("old-job");
        when(jobs.getStatus(user, exercise)).thenReturn(Optional.of(replay));
        when(replay.withRun(any())).thenReturn(replay);
        when(replay.withRevertAvailability(false, null, null)).thenReturn(replay);
        assertThat(history.status(12L, "old-job", user)).isSameAs(replay);
        verify(replay).withRun(any());
    }

    @Test
    void expiredReplayDoesNotExpireDurableRecovery() {
        when(recovery.findRevertibleRun(12L)).thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertibleRun("old-job", GenerationMode.ADAPT)));
        when(exercises.isUnreleasedAndWithoutStudentParticipations(12L)).thenReturn(true);
        var result = history.status(12L, "old-job", user);
        assertThat(result.revertAvailable()).isTrue();
        assertThat(result.revertJobId()).isEqualTo("old-job");
        assertThat(result.revertMode()).isEqualTo(GenerationMode.ADAPT);
        assertThat(result.accountingState()).isEqualTo(ExerciseGenerationAccountingState.INCOMPLETE);
    }

    @Test
    void retainedReplayGetsRecoveryFromVersionsRatherThanItsTransientFlags() {
        when(recovery.findRevertibleRun(12L)).thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertibleRun("old-job", GenerationMode.ADAPT)));
        when(exercises.isUnreleasedAndWithoutStudentParticipations(12L)).thenReturn(true);
        when(jobs.getStatus(user, exercise)).thenReturn(Optional.of(new ExerciseGenerationStatusDTO("old-job", false, GenerationMode.ADAPT, List.of(), List.of(), false)));
        assertThat(history.status(12L, "old-job", user).revertAvailable()).isTrue();
    }

    @Test
    void newerMutationAndLiveWritersCannotOfferUndoForTheSelectedOlderRun() {
        when(recovery.findRevertibleRun(12L)).thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertibleRun("new-job", GenerationMode.ADAPT)));
        assertThat(history.status(12L, "old-job", user).revertAvailable()).isFalse();
        when(recovery.findRevertibleRun(12L)).thenReturn(Optional.of(new ExerciseGenerationRevertService.RevertibleRun("old-job", GenerationMode.ADAPT)));
        when(exercises.isUnreleasedAndWithoutStudentParticipations(12L)).thenReturn(true);
        when(jobs.hasActiveJob(12L)).thenReturn(true);
        assertThat(history.status(12L, "old-job", user).revertAvailable()).isFalse();
        when(jobs.isRevertRecoveryPending(12L)).thenReturn(true);
        assertThat(history.status(12L, "old-job", user).revertAvailable()).isTrue();
        when(exercises.isUnreleasedAndWithoutStudentParticipations(12L)).thenReturn(false);
        assertThat(history.status(12L, "old-job", user).revertAvailable()).isFalse();
    }

    @Test
    void anotherOwnerCannotReadEvenTheExistenceOfARun() {
        when(run.getOwnerId()).thenReturn(99L);
        assertThatThrownBy(() -> history.status(12L, "old-job", user)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(exercises, authorization, jobs);
    }

    @Test
    void deletedOwnerCannotBeReplacedByTheCurrentCaller() {
        when(run.getOwnerId()).thenReturn(null);
        assertThatThrownBy(() -> history.status(12L, "old-job", user)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(jobs);
    }

    @Test
    void revokedCourseAuthorityDeniesPreviouslyOwnedHistory() {
        when(authorization.isAtLeastEditorForExercise(exercise, user)).thenReturn(false);
        assertThatThrownBy(() -> history.status(12L, "old-job", user)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(jobs);
    }

    @Test
    void destinationCannotBeSubstitutedInCanonicalLookup() {
        assertThatThrownBy(() -> history.status(99L, "old-job", user)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(exercises, jobs);
    }

    @Test
    void listsAtMostFiftyRunsAndBatchesExerciseLoading() {
        var rows = IntStream.range(0, 51).mapToObj(i -> {
            var row = mock(AuthoringRun.class);
            when(row.getId()).thenReturn(100L - i);
            when(row.getExerciseId()).thenReturn(12L);
            when(row.getJobId()).thenReturn("job-" + i);
            when(row.getStatus()).thenReturn(AuthoringRun.Status.QUEUED);
            return row;
        }).toList();
        when(runs.findOwnedBefore(eq(7L), eq(null), any())).thenReturn(rows);
        when(exercises.findAllWithEagerCourseAndExamByIdIn(Set.of(12L))).thenReturn(List.of(exercise));
        var page = history.history(user, null);
        assertThat(page.runs()).hasSize(50).allSatisfy(row -> assertThat(row.status()).isEqualTo(AuthoringRun.Status.UNKNOWN));
        assertThat(page.nextBeforeId()).isEqualTo(51L);
        var request = ArgumentCaptor.forClass(Pageable.class);
        verify(runs).findOwnedBefore(eq(7L), eq(null), request.capture());
        assertThat(request.getValue().getPageSize()).isEqualTo(51);
        verify(exercises).findAllWithEagerCourseAndExamByIdIn(Set.of(12L));
    }

    @Test
    void historyDoesNotExposeRunsAfterCoursePermissionIsRemoved() {
        when(runs.findOwnedBefore(eq(7L), eq(42L), any())).thenReturn(List.of(run));
        when(exercises.findAllWithEagerCourseAndExamByIdIn(Set.of(12L))).thenReturn(List.of(exercise));
        when(authorization.isAtLeastEditorForExercise(exercise, user)).thenReturn(false);
        assertThat(history.history(user, 42L).runs()).isEmpty();
        verifyNoInteractions(jobs);
    }

    @Test
    void reauthorizesFiveHundredRunsWithOneQueryPerEntityAndOneCheckPerDestination() {
        var ids = IntStream.range(0, 500).mapToObj(i -> "retained-" + i).toList();
        var owned = ids.stream().map(id -> {
            var candidate = new AuthoringRun();
            candidate.setJobId(id);
            candidate.setExerciseId(12L);
            return candidate;
        }).toList();
        when(runs.findByOwnerIdAndJobIdIn(7L, ids)).thenReturn(owned);
        when(exercises.findAllWithEagerCourseAndExamByIdIn(Set.of(12L))).thenReturn(List.of(exercise));

        assertThat(history.authorizedRunIds(user, ids)).containsExactlyElementsOf(ids);

        verify(runs).findByOwnerIdAndJobIdIn(7L, ids);
        verify(exercises).findAllWithEagerCourseAndExamByIdIn(Set.of(12L));
        verify(authorization).isAtLeastEditorForExercise(exercise, user);
        verifyNoInteractions(jobs, recovery);
    }

    @Test
    void bulkAccessOmitsRevokedAndMissingDestinationsWithoutLoadingReplay() {
        when(runs.findByOwnerIdAndJobIdIn(7L, List.of("old-job"))).thenReturn(List.of(run));
        when(exercises.findAllWithEagerCourseAndExamByIdIn(Set.of(12L))).thenReturn(List.of(exercise));
        when(authorization.isAtLeastEditorForExercise(exercise, user)).thenReturn(false);
        assertThat(history.authorizedRunIds(user, List.of("old-job"))).isEmpty();
        when(exercises.findAllWithEagerCourseAndExamByIdIn(Set.of(12L))).thenReturn(List.of());
        assertThat(history.authorizedRunIds(user, List.of("old-job"))).isEmpty();
        verifyNoInteractions(jobs, recovery);
    }

    @Test
    void bulkAccessDoesNotLoadDestinationsForAnotherOwnersIdentities() {
        when(runs.findByOwnerIdAndJobIdIn(7L, List.of("foreign"))).thenReturn(List.of());
        assertThat(history.authorizedRunIds(user, List.of("foreign"))).isEmpty();
        verifyNoInteractions(exercises, authorization, jobs, recovery);
    }

    @Test
    void absenceOfATerminalRecordDoesNotInventATerminalTimestampOrSuccess() {
        when(run.getFinishedAt()).thenReturn(null);
        var status = history.status(12L, "old-job", user);
        assertThat(status.events()).isEmpty();
        assertThat(status.running()).isFalse();
        assertThat(status.usage()).isNull();
    }
}
