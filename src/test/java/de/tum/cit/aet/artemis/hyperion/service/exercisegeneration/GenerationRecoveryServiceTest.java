package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.service.ExerciseEditorSyncService;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewService;
import de.tum.cit.aet.artemis.hyperion.domain.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Tests the recovery path: a near-miss run becomes a persisted draft plus one problem-statement-anchored {@code CONSISTENCY_CHECK} finding per verification reason (and the agent's
 * note), with the editor notified live. Pins the safety boundary that an accepted-shaped outcome produces no recovery findings.
 */
class GenerationRecoveryServiceTest {

    private GenerationPersistenceService persistenceService;

    private ExerciseReviewService exerciseReviewService;

    private ExerciseEditorSyncService exerciseEditorSyncService;

    private GenerationRecoveryService recoveryService;

    private ProgrammingExercise exercise;

    private final User user = new User();

    @BeforeEach
    void setUp() {
        persistenceService = mock(GenerationPersistenceService.class);
        exerciseReviewService = mock(ExerciseReviewService.class);
        exerciseEditorSyncService = mock(ExerciseEditorSyncService.class);
        recoveryService = new GenerationRecoveryService(persistenceService, exerciseReviewService, exerciseEditorSyncService);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        // Default: a from-scratch target — the draft was committed to the live exercise in place (nothing to lose). Adapt-specific tests override this.
        when(persistenceService.persistRecoveryDraft(any(), any(), any(), any())).thenReturn(new GenerationPersistenceService.RecoveryPersistResult(false, null));
    }

    private GenerationOutcome outcome(VerificationResult verification, String agentNote) {
        GenerationOutcome outcome = mock(GenerationOutcome.class);
        when(outcome.verification()).thenReturn(verification);
        when(outcome.loopResult()).thenReturn(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 5, agentNote));
        // No advisory spec-fidelity findings by default; tests that exercise the advisory path override this.
        when(outcome.specFidelityReport()).thenReturn(SpecFidelityReport.empty());
        return outcome;
    }

    @Test
    void toFindings_oneFindingPerReasonPlusAgentNote_allAnchoredToProblemStatement() {
        VerificationResult verification = new VerificationResult(false, false, true, 4,
                List.of("The solution does not pass its own tests (1 failing of 4).", "These [task] bindings reference names that match no actual test: [sortAscending]."));
        List<ConsistencyIssueDTO> findings = GenerationRecoveryService.toFindings(outcome(verification, "I converged on 3 of 4 tests."));

        // 2 verification reasons + 1 agent note = 3 findings.
        assertThat(findings).hasSize(3);
        // Every finding anchors to the problem statement (which always exists), so the thread reliably lands in the editor.
        assertThat(findings).allSatisfy(finding -> {
            assertThat(finding.relatedLocations()).singleElement().satisfies(location -> assertThat(location.type()).isEqualTo(ArtifactType.PROBLEM_STATEMENT));
            assertThat(finding.description()).isNotBlank();
            assertThat(finding.suggestedFix()).isNotBlank();
        });
        // The concrete gate text is preserved in the finding so the instructor sees exactly what to fix.
        assertThat(findings).anyMatch(finding -> finding.description().contains("The solution does not pass its own tests"));
        assertThat(findings).anyMatch(finding -> finding.description().contains("reference names that match no actual test"));
        // The agent's note is carried at a lower severity than the hard verification gaps.
        assertThat(findings).filteredOn(finding -> finding.severity() == Severity.MEDIUM).singleElement()
                .satisfies(finding -> assertThat(finding.description()).contains("I converged on 3 of 4 tests."));
        assertThat(findings).filteredOn(finding -> finding.severity() == Severity.HIGH).hasSize(2);
    }

    @Test
    void toFindings_blankReasonsAndNote_areSkipped() {
        VerificationResult verification = new VerificationResult(false, false, true, 1, List.of("  ", "A real gap."));
        List<ConsistencyIssueDTO> findings = GenerationRecoveryService.toFindings(outcome(verification, "   "));
        // The blank reason and the blank agent note are dropped; only the one real gap survives.
        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().description()).contains("A real gap.");
    }

    @Test
    void recover_persistsDraft_createsReviewThreads_andNotifiesEditors() {
        VerificationResult verification = new VerificationResult(false, false, true, 2, List.of("gap one", "gap two"));
        GenerationOutcome outcome = outcome(verification, "");
        // Two persisted threads come back from the review service.
        when(exerciseReviewService.createConsistencyCheckThreads(eq(42L), any())).thenReturn(List.of(threadWithComment(), threadWithComment()));

        int created = recoveryService.recover(exercise, user, outcome, "job-1").reviewThreadCount();

        assertThat(created).isEqualTo(2);
        // The best-effort draft is persisted through the recovery-safe path (which never regresses a working exercise) — for a from-scratch target this commits it in place.
        verify(persistenceService).persistRecoveryDraft(exercise, user, outcome, "job-1");
        // The findings are created through the EXISTING consistency-check thread path (no parallel mechanism).
        ArgumentCaptor<List<ConsistencyIssueDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(exerciseReviewService).createConsistencyCheckThreads(eq(42L), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        // Open editors are notified per created thread so the review panel updates live.
        verify(exerciseEditorSyncService, times(2)).broadcastReviewThreadUpdate(eq(42L), any());
    }

    @Test
    void recover_persistsBeforeCreatingFindings_soDraftExistsWhenThreadsAnchor() {
        VerificationResult verification = new VerificationResult(false, false, true, 1, List.of("gap"));
        GenerationOutcome outcome = outcome(verification, "");
        when(exerciseReviewService.createConsistencyCheckThreads(anyLong(), any())).thenReturn(List.of(threadWithComment()));

        var inOrder = org.mockito.Mockito.inOrder(persistenceService, exerciseReviewService);
        recoveryService.recover(exercise, user, outcome, "job-1");

        // Persist must happen before threads are created so the draft (and its repos) exist when the review threads resolve their anchors.
        inOrder.verify(persistenceService).persistRecoveryDraft(exercise, user, outcome, "job-1");
        inOrder.verify(exerciseReviewService).createConsistencyCheckThreads(eq(42L), any());
    }

    @Test
    void recover_noFindings_persistsDraftButCreatesNoThreads() {
        // A degenerate non-accepted outcome with no reasons and no note (should not happen in practice, but must not crash or spam empty threads).
        VerificationResult verification = new VerificationResult(false, false, true, 1, List.of());
        GenerationOutcome outcome = outcome(verification, "");

        int created = recoveryService.recover(exercise, user, outcome, "job-1").reviewThreadCount();

        assertThat(created).isZero();
        verify(persistenceService).persistRecoveryDraft(exercise, user, outcome, "job-1");
        verify(exerciseReviewService, never()).createConsistencyCheckThreads(anyLong(), any());
        verify(exerciseEditorSyncService, never()).broadcastReviewThreadUpdate(anyLong(), any());
    }

    /** When persist succeeds but the review-comment annotation throws, recover swallows it and returns the degraded sentinel rather than propagating (the draft IS saved). */
    @Test
    void recover_persistSucceedsThenThreadCreationThrows_returnsDegradedSentinel_neverPropagates() {
        VerificationResult verification = new VerificationResult(false, false, true, 2, List.of("gap one", "gap two"));
        GenerationOutcome outcome = outcome(verification, "agent note");
        // Persist commits the draft (no throw), then thread creation blows up (e.g. a DB constraint / missing exercise version).
        when(exerciseReviewService.createConsistencyCheckThreads(anyLong(), any())).thenThrow(new IllegalStateException("thread persistence failed"));

        // The draft is saved, so recovery returns the degraded sentinel instead of throwing — the caller will emit NEEDS_REVIEW, not PARTIAL.
        int result = recoveryService.recover(exercise, user, outcome, "job-1").reviewThreadCount();
        assertThat(result).isEqualTo(GenerationRecoveryService.REVIEW_COMMENTS_FAILED);

        // The draft WAS committed; the annotation failure did not undo it and did not masquerade as "nothing saved".
        verify(persistenceService).persistRecoveryDraft(exercise, user, outcome, "job-1");
        // No editor broadcast happened (the thread step failed before it), but that degradation is now surfaced via the sentinel rather than a PARTIAL mislabel.
        verify(exerciseEditorSyncService, never()).broadcastReviewThreadUpdate(anyLong(), any());
    }

    /** An adapt draft diverted to an isolated branch surfaces {@code liveExerciseUntouched=true} and the draft branch up to the caller. */
    @Test
    void recover_adaptTargetDivertedToIsolatedBranch_resultCarriesUntouchedFlagAndBranch() {
        VerificationResult verification = new VerificationResult(false, false, true, 2, List.of("gap one"));
        GenerationOutcome outcome = outcome(verification, "");
        // The persistence layer detected an adapt target and diverted the draft to an isolated branch, leaving the live exercise byte-identical.
        when(persistenceService.persistRecoveryDraft(any(), any(), any(), eq("job-9")))
                .thenReturn(new GenerationPersistenceService.RecoveryPersistResult(true, "hyperion-draft/job-9"));
        when(exerciseReviewService.createConsistencyCheckThreads(eq(42L), any())).thenReturn(List.of(threadWithComment()));

        GenerationRecoveryService.RecoveryResult result = recoveryService.recover(exercise, user, outcome, "job-9");

        // The live exercise was NOT overwritten and the caller learns exactly where the draft is.
        assertThat(result.liveExerciseUntouched()).isTrue();
        assertThat(result.draftBranch()).isEqualTo("hyperion-draft/job-9");
        // Review comments are still created so the gaps are surfaced even though the draft is on an isolated branch.
        assertThat(result.reviewThreadCount()).isEqualTo(1);
    }

    /** The adapt diversion survives a degraded annotation: the result still carries {@code liveExerciseUntouched=true} and the branch even when thread creation fails. */
    @Test
    void recover_adaptDiverted_thenAnnotationFails_stillReportsUntouchedAndBranch() {
        VerificationResult verification = new VerificationResult(false, false, true, 1, List.of("gap"));
        GenerationOutcome outcome = outcome(verification, "");
        when(persistenceService.persistRecoveryDraft(any(), any(), any(), eq("job-x")))
                .thenReturn(new GenerationPersistenceService.RecoveryPersistResult(true, "hyperion-draft/job-x"));
        when(exerciseReviewService.createConsistencyCheckThreads(anyLong(), any())).thenThrow(new IllegalStateException("thread persistence failed"));

        GenerationRecoveryService.RecoveryResult result = recoveryService.recover(exercise, user, outcome, "job-x");

        assertThat(result.reviewThreadCount()).isEqualTo(GenerationRecoveryService.REVIEW_COMMENTS_FAILED);
        assertThat(result.liveExerciseUntouched()).isTrue();
        assertThat(result.draftBranch()).isEqualTo("hyperion-draft/job-x");
    }

    /** A null verification with a non-empty agent note yields exactly the note finding and never crashes on the null branch in {@code toFindings}. */
    @Test
    void toFindings_nullVerification_withAgentNote_yieldsOnlyTheNoteFinding() {
        GenerationOutcome outcome = mock(GenerationOutcome.class);
        when(outcome.verification()).thenReturn(null);
        when(outcome.loopResult()).thenReturn(new AgentLoopResult(AgentLoopResult.Status.BUDGET_EXHAUSTED, 100, "Ran out of turns mid-fix."));
        when(outcome.specFidelityReport()).thenReturn(SpecFidelityReport.empty());

        List<ConsistencyIssueDTO> findings = GenerationRecoveryService.toFindings(outcome);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.MEDIUM);
            assertThat(finding.description()).contains("Ran out of turns mid-fix.");
        });
    }

    /** A hostile reason (control chars, RTL, emoji, very long) survives trimmed and a null reason is dropped, so a malformed verifier reason cannot crash thread mapping. */
    @Test
    void toFindings_hostileAndNullReasons_doNotCrash_andSurviveSanely() {
        String hostile = "  ‮gap‬ 💩  \t" + "X".repeat(50_000) + "  ";
        VerificationResult verification = new VerificationResult(false, false, true, 1, java.util.Arrays.asList(null, hostile, ""));
        GenerationOutcome outcome = outcome(verification, null);

        List<ConsistencyIssueDTO> findings = GenerationRecoveryService.toFindings(outcome);

        // null and blank reasons dropped; the hostile one survives (trimmed) and carries a single problem-statement anchor. A null agent note is tolerated (no extra finding).
        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.description()).contains("gap").contains("💩");
            assertThat(finding.relatedLocations()).singleElement().satisfies(location -> assertThat(location.type()).isEqualTo(ArtifactType.PROBLEM_STATEMENT));
        });
    }

    // --- Advisory spec-fidelity surfacing -----------------------------------------------------------------------------------------------------------------------------------

    /** On the recovery path, advisory spec-fidelity findings ride along as MEDIUM comments next to the HIGH verification gaps. */
    @Test
    void toFindings_includesAdvisorySpecFidelityGaps() {
        VerificationResult verification = new VerificationResult(false, false, true, 2, List.of("gap one"));
        GenerationOutcome outcome = outcome(verification, "");
        when(outcome.specFidelityReport())
                .thenReturn(new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "CJK characters", "no CJK test"),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MECHANICS_LEAK, "make the tests fail", "leaked phrasing"))));

        List<ConsistencyIssueDTO> findings = GenerationRecoveryService.toFindings(outcome);

        // 1 hard verification gap (HIGH) + 2 advisory spec-fidelity gaps (MEDIUM).
        assertThat(findings).hasSize(3);
        assertThat(findings).anyMatch(finding -> finding.description().contains("CJK characters"));
        assertThat(findings).anyMatch(finding -> finding.description().contains("make the tests fail"));
        assertThat(findings).filteredOn(finding -> finding.description().contains("CJK characters")).singleElement()
                .satisfies(finding -> assertThat(finding.severity()).isEqualTo(Severity.MEDIUM));
    }

    /** On the accepted path, {@code surfaceAdvisoryFindings} attaches advisory threads, returns the count, and notifies editors without changing acceptance. */
    @Test
    void surfaceAdvisoryFindings_attachesThreadsWithoutAffectingAcceptance() {
        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "emoji", "no emoji test")));
        when(exerciseReviewService.createConsistencyCheckThreads(eq(42L), any())).thenReturn(List.of(threadWithComment()));

        int created = recoveryService.surfaceAdvisoryFindings(exercise, report);

        assertThat(created).isEqualTo(1);
        verify(exerciseReviewService).createConsistencyCheckThreads(eq(42L), any());
        verify(exerciseEditorSyncService, times(1)).broadcastReviewThreadUpdate(eq(42L), any());
    }

    /** An empty advisory report on the accepted path creates no threads and never calls the review service — a clean exercise gets zero advisory noise. */
    @Test
    void surfaceAdvisoryFindings_emptyReport_createsNothing() {
        int created = recoveryService.surfaceAdvisoryFindings(exercise, SpecFidelityReport.empty());

        assertThat(created).isZero();
        verify(exerciseReviewService, never()).createConsistencyCheckThreads(anyLong(), any());
    }

    /** A thread-creation failure on the accepted path is swallowed (advisory only): it returns 0 and never throws, so it can never downgrade a SUCCESS. */
    @Test
    void surfaceAdvisoryFindings_attachFailure_isSwallowed() {
        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "emoji", "no emoji test")));
        when(exerciseReviewService.createConsistencyCheckThreads(anyLong(), any())).thenThrow(new IllegalStateException("db down"));

        int created = recoveryService.surfaceAdvisoryFindings(exercise, report);

        assertThat(created).isZero();
    }

    /**
     * A minimally-populated persisted thread, as createConsistencyCheckThreads returns. Uses a real entity (not a mock) so the CommentThreadDTO mapping in the editor-sync
     * broadcast
     * resolves its getters without NPEs; an empty comment set is fine because the broadcast only carries metadata.
     */
    private CommentThread threadWithComment() {
        CommentThread thread = new CommentThread();
        thread.setId(threadIdSequence++);
        thread.setExercise(exercise);
        thread.setTargetType(de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType.PROBLEM_STATEMENT);
        thread.setInitialLineNumber(1);
        thread.setOutdated(false);
        thread.setResolved(false);
        return thread;
    }

    private long threadIdSequence = 1;
}
