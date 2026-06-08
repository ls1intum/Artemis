package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Tests terminal-state handling of the async generation task: an accepted run persists clean ({@code SUCCESS}); a non-accepted run is recovered into a draft with review comments
 * ({@code NEEDS_REVIEW}); and a recovery failure falls back to {@code PARTIAL}. Pins the safety boundary that only the accepted path is a clean success.
 */
class ExerciseGenerationTaskServiceTest {

    private ExerciseGenerationOrchestrationService orchestrator;

    private GenerationPersistenceService persistenceService;

    private GenerationRecoveryService recoveryService;

    private HyperionWebsocketService websocket;

    private ExerciseGenerationJobService jobService;

    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ExerciseGenerationTaskService taskService;

    private ProgrammingExercise exercise;

    private final User user = new User();

    @BeforeEach
    void setUp() {
        orchestrator = mock(ExerciseGenerationOrchestrationService.class);
        persistenceService = mock(GenerationPersistenceService.class);
        recoveryService = mock(GenerationRecoveryService.class);
        websocket = mock(HyperionWebsocketService.class);
        jobService = mock(ExerciseGenerationJobService.class);
        programmingExerciseRepository = mock(ProgrammingExerciseRepository.class);
        taskService = new ExerciseGenerationTaskService(orchestrator, persistenceService, recoveryService, websocket, jobService, programmingExerciseRepository);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(7L);
        // The task re-loads the exercise with its associations eagerly initialized; return the same mock so the terminal-state assertions are unaffected.
        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndBuildConfigById(7L)).thenReturn(Optional.of(exercise));
        user.setLogin("instructor1");
        when(jobService.isCancelled(anyString())).thenReturn(false);
    }

    private GenerationOutcome stubOutcome(AgentLoopResult.Status status, VerificationResult verification, String finalMessage) {
        GenerationOutcome outcome = mock(GenerationOutcome.class);
        when(outcome.loopResult()).thenReturn(new AgentLoopResult(status, 3, finalMessage));
        when(outcome.verification()).thenReturn(verification);
        when(outcome.isAccepted()).thenReturn(verification != null && verification.accepted());
        when(orchestrator.generate(any(), any(), anyString(), anyString(), any(), any())).thenReturn(outcome);
        return outcome;
    }

    /** Captures the terminal (DONE/ERROR/CANCELLED) event recorded for the run. */
    private ExerciseGenerationEventDTO terminalEvent() {
        ArgumentCaptor<ExerciseGenerationEventDTO> captor = ArgumentCaptor.forClass(ExerciseGenerationEventDTO.class);
        verify(jobService, atLeastOnce()).recordEvent(anyLong(), anyString(), captor.capture(), anyBoolean());
        return captor.getAllValues().stream().filter(event -> event.type() == ExerciseGenerationEventDTO.Type.DONE || event.type() == ExerciseGenerationEventDTO.Type.ERROR
                || event.type() == ExerciseGenerationEventDTO.Type.CANCELLED).reduce((first, second) -> second).orElseThrow();
    }

    private void run() {
        taskService.runAsync(new ExerciseGenerationStartedEvent("job-1", user, exercise, "do it"));
    }

    @Test
    void acceptedOutcome_persistsCleanly_withoutRecoveryOrReviewComments() {
        VerificationResult accepted = new VerificationResult(true, true, true, 4, List.of());
        GenerationOutcome outcome = stubOutcome(AgentLoopResult.Status.COMPLETED, accepted, "");

        run();

        // The accepted path persists clean and never touches the recovery (review-comment) path.
        verify(persistenceService).persist(exercise, user, outcome);
        verify(recoveryService, never()).recover(any(), any(), any(), anyString());

        ExerciseGenerationEventDTO terminal = terminalEvent();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.SUCCESS);
    }

    @Test
    void rejectedOutcome_isRecovered_emitsNeedsReview_withIssueCountFromVerificationFindings() {
        VerificationResult rejected = new VerificationResult(false, false, true, 4,
                List.of("The solution does not pass its own tests.", "The template runs a different number of tests than the solution."));
        stubOutcome(AgentLoopResult.Status.COMPLETED, rejected, "I ran out of turns fixing the last failing test.");
        // Recovery persists the best-effort draft and creates review comments; report it created 3 threads (2 findings + 1 agent note) and committed in place (from-scratch
        // target).
        when(recoveryService.recover(eq(exercise), eq(user), any(), anyString())).thenReturn(new GenerationRecoveryService.RecoveryResult(3, false, null));

        run();

        // A rejected run no longer discards the work: it recovers (which itself persists the draft) and never uses the clean accepted-persist path directly.
        verify(recoveryService).recover(eq(exercise), eq(user), any(), anyString());
        verify(persistenceService, never()).persist(any(), any(), any());

        ExerciseGenerationEventDTO terminal = terminalEvent();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        // The distinct NEEDS_REVIEW verdict — never SUCCESS — so the UI shows "draft generated, N issues to review".
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.message()).contains("3 issue(s) to review");
        // The structured verdict is mirrored so the client can render scannable chips for the failed gates.
        assertThat(terminal.verdict()).isNotNull();
        assertThat(terminal.verdict().accepted()).isFalse();
    }

    /** A failed adapt diverted to an isolated branch surfaces a NEEDS_REVIEW message that says the live exercise was left unchanged and names the draft branch. */
    @Test
    void rejectedAdapt_divertedToIsolatedBranch_messageSaysLiveExerciseUntouchedAndNamesBranch() {
        VerificationResult rejected = new VerificationResult(false, false, true, 4, List.of("The solution does not pass its own tests."));
        stubOutcome(AgentLoopResult.Status.COMPLETED, rejected, "");
        // Recovery diverted the adapt draft to an isolated branch and left the live exercise byte-identical.
        when(recoveryService.recover(eq(exercise), eq(user), any(), anyString())).thenReturn(new GenerationRecoveryService.RecoveryResult(1, true, "hyperion-draft/job-1"));

        run();

        ExerciseGenerationEventDTO terminal = terminalEvent();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        // The instructor is explicitly told the working exercise was preserved and where to find the draft.
        assertThat(terminal.message()).contains("left unchanged").contains("hyperion-draft/job-1");
    }

    /** A from-scratch rejected run committed in place must NOT claim anything was left unchanged — the "left unchanged" wording is adapt-specific. */
    @Test
    void rejectedFromScratch_committedInPlace_messageDoesNotClaimUntouched() {
        VerificationResult rejected = new VerificationResult(false, false, true, 4, List.of("The solution does not pass its own tests."));
        stubOutcome(AgentLoopResult.Status.COMPLETED, rejected, "");
        when(recoveryService.recover(eq(exercise), eq(user), any(), anyString())).thenReturn(new GenerationRecoveryService.RecoveryResult(2, false, null));

        run();

        ExerciseGenerationEventDTO terminal = terminalEvent();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.message()).contains("2 issue(s) to review").doesNotContain("left unchanged").doesNotContain("hyperion-draft/");
    }

    @Test
    void recoveryFailure_fallsBackToPartial_neverLooksAccepted() {
        VerificationResult rejected = new VerificationResult(false, true, false, 2, List.of("The template passes the tests, but it must fail them."));
        stubOutcome(AgentLoopResult.Status.BUDGET_EXHAUSTED, rejected, "");
        doThrow(new IllegalStateException("could not commit the draft")).when(recoveryService).recover(any(), any(), any(), anyString());

        run();

        ExerciseGenerationEventDTO terminal = terminalEvent();
        // Recovery threw, so we fall back to PARTIAL: the instructor learns nothing reliable was saved and can retry. Crucially it is NOT reported as SUCCESS or NEEDS_REVIEW.
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.PARTIAL);
        assertThat(terminal.message()).contains("could not commit the draft");
    }

    /**
     * When recovery returns the degraded sentinel (draft saved, annotation failed), the run is NEEDS_REVIEW (never PARTIAL) with a "review notes could not be attached" message.
     */
    @Test
    void rejectedRun_draftPersistedButAnnotationFails_reportsNeedsReviewDegraded_notPartial() {
        VerificationResult rejected = new VerificationResult(false, false, true, 4, List.of("The solution does not pass its own tests."));
        stubOutcome(AgentLoopResult.Status.COMPLETED, rejected, "");
        // recover() swallowed the annotation failure and returned the degraded sentinel (the draft was committed first).
        when(recoveryService.recover(eq(exercise), eq(user), any(), anyString()))
                .thenReturn(new GenerationRecoveryService.RecoveryResult(GenerationRecoveryService.REVIEW_COMMENTS_FAILED, false, null));

        run();

        ExerciseGenerationEventDTO terminal = terminalEvent();
        assertThat(terminal.completionStatus()).isEqualTo(ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW);
        assertThat(terminal.message()).contains("review notes could not be attached");
    }

    /** When {@code persist} throws on the accepted path, the run surfaces as ERROR (not a silent half-save) and recovery is never reached. */
    @Test
    void persistThrowsOnAcceptedPath_surfacesError_neverRecovers() {
        VerificationResult accepted = new VerificationResult(true, true, true, 4, List.of());
        stubOutcome(AgentLoopResult.Status.COMPLETED, accepted, "");
        doThrow(new IllegalStateException("git push rejected")).when(persistenceService).persist(any(), any(), any());

        run();

        ExerciseGenerationEventDTO terminal = terminalEvent();
        assertThat(terminal.type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
        assertThat(terminal.message()).contains("Verification passed but saving the exercise failed");
        verify(recoveryService, never()).recover(any(), any(), any(), anyString());
    }

    @Test
    void cancelledOutcome_changesNothing_noPersistNoRecovery() {
        GenerationOutcome outcome = mock(GenerationOutcome.class);
        when(outcome.loopResult()).thenReturn(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));
        when(orchestrator.generate(any(), any(), anyString(), anyString(), any(), any())).thenReturn(outcome);

        run();

        verify(persistenceService, never()).persist(any(), any(), any());
        verify(recoveryService, never()).recover(any(), any(), any(), anyString());
        assertThat(terminalEvent().type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED);
    }

    @Test
    void errorOutcome_changesNothing_noPersistNoRecovery() {
        GenerationOutcome outcome = mock(GenerationOutcome.class);
        when(outcome.loopResult()).thenReturn(new AgentLoopResult(AgentLoopResult.Status.ERROR, 2, ""));
        when(outcome.errorMessage()).thenReturn("the agent loop ended with an error");
        when(orchestrator.generate(any(), any(), anyString(), anyString(), any(), any())).thenReturn(outcome);

        run();

        verify(persistenceService, never()).persist(any(), any(), any());
        verify(recoveryService, never()).recover(any(), any(), any(), anyString());
        assertThat(terminalEvent().type()).isEqualTo(ExerciseGenerationEventDTO.Type.ERROR);
    }

}
