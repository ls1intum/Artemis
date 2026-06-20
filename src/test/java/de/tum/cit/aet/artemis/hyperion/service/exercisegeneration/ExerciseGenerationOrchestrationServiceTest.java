package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Unit tests for the orchestrator's verifier-feedback retry loop. All collaborators are Mockito mocks, so the loop's control flow (retry on rejection, stop on acceptance, bound on
 * attempts, cancellation short-circuit, session teardown on error) is exercised deterministically with no Docker, LLM, or Hazelcast.
 */
class ExerciseGenerationOrchestrationServiceTest {

    private static final int MAX_GENERATION_ATTEMPTS = 3;

    private InteractiveSandbox sandbox;

    private AgentLoopRunner agentLoopRunner;

    private AuthoritativeVerificationService verifier;

    private StructuralOracleSeedingService structuralOracleSeeder;

    private SpecFidelityCriticService specFidelityCritic;

    private GenerationWorkspaceService workspace;

    private ExerciseGenerationOrchestrationService service;

    private ProgrammingExercise exercise;

    private User user;

    private static final String JOB_ID = "job-1";

    private static final String SESSION_ID = "session-abc";

    @BeforeEach
    void setUp() {
        sandbox = mock(InteractiveSandbox.class);
        workspace = mock(GenerationWorkspaceService.class);
        agentLoopRunner = mock(AgentLoopRunner.class);
        verifier = mock(AuthoritativeVerificationService.class);
        AgentSystemPromptService systemPromptFactory = mock(AgentSystemPromptService.class);
        structuralOracleSeeder = mock(StructuralOracleSeedingService.class);
        specFidelityCritic = mock(SpecFidelityCriticService.class);
        ExerciseGenerationJobService jobService = mock(ExerciseGenerationJobService.class);
        LLMTokenUsageService llmTokenUsageService = mock(LLMTokenUsageService.class);

        when(sandbox.createSession(any())).thenReturn(SESSION_ID);
        when(systemPromptFactory.build(any())).thenReturn("SYSTEM_PROMPT");
        // Default to a successful, empty extraction (the verifier is mocked, so files are not inspected here).
        when(workspace.extractRepository(any(), anyString(), any())).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(java.util.Map.of(), false));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("PROBLEM STATEMENT");
        // Default the advisory critic to no findings; specific tests override it.
        when(specFidelityCritic.critique(any(), any(), any())).thenReturn(SpecFidelityReport.empty());
        // renderForRetryPrompt is a pure renderer; delegate to the real impl so the retry prompt is folded exactly as in production.
        SpecFidelityCriticService renderingDelegate = new SpecFidelityCriticService(null, new com.fasterxml.jackson.databind.ObjectMapper());
        when(specFidelityCritic.renderForRetryPrompt(any())).thenAnswer(invocation -> renderingDelegate.renderForRetryPrompt(invocation.getArgument(0)));

        service = new ExerciseGenerationOrchestrationService(Optional.of(sandbox), workspace, agentLoopRunner, verifier, systemPromptFactory, structuralOracleSeeder,
                specFidelityCritic, jobService, llmTokenUsageService, 100);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(null);
        user = mock(User.class);
        when(user.getId()).thenReturn(7L);
    }

    private static AgentLoopResult completed() {
        return new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 3, "done");
    }

    private static VerificationResult accepted() {
        return new VerificationResult(true, true, true, 5, List.of());
    }

    private static VerificationResult rejected(String reason) {
        return new VerificationResult(false, false, true, 5, List.of(reason));
    }

    private GenerationOutcome generate(BooleanSupplier cancelled) {
        return service.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, cancelled, null);
    }

    /** A rejected first attempt feeds its verification report into the next prompt, and a subsequent accepted attempt yields an accepted outcome. */
    @Test
    void rejectedThenAccepted_feedsReportIntoNextPromptAndAccepts() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(rejected("template unexpectedly passed all tests"), accepted());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("the second, accepted attempt yields an accepted outcome").isTrue();
        }

        verify(agentLoopRunner, times(2)).run(anyString(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts.get(0)).as("the first prompt is the instructor brief").isEqualTo("Build a bubble sort exercise.");
        assertThat(prompts.get(1)).as("the second prompt carries the verifier's rejection report so the agent can fix exactly those issues")
                .contains("template unexpectedly passed all tests").contains("rejected by the authoritative verifier");
    }

    /** Acceptance on the first attempt runs the agent exactly once — no needless retry. */
    @Test
    void acceptedOnFirstAttempt_runsAgentExactlyOnce() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(1)).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    /** All attempts rejected runs exactly {@code MAX_GENERATION_ATTEMPTS} times and returns a non-accepted outcome. */
    @Test
    void allAttemptsRejected_runsMaxAttemptsAndReturnsNotAccepted() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(rejected("still failing"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("an exercise rejected on every attempt is not accepted").isFalse();
        }

        verify(agentLoopRunner, times(MAX_GENERATION_ATTEMPTS)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(MAX_GENERATION_ATTEMPTS)).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    /** A CANCELLED loop result short-circuits before verification and destroys the session. */
    @Test
    void cancelledLoopResult_skipsVerificationAndDestroysSession() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));

        GenerationOutcome outcome = generate(() -> false);

        assertThat(outcome.isAccepted()).isFalse();
        verify(verifier, never()).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
        verify(sandbox).destroySession(SESSION_ID);
    }

    /** A cancellation flag flipping true between the loop turn and verification short-circuits before verification and destroys the session. */
    @Test
    void cancellationBetweenTurns_skipsVerificationAndDestroysSession() {
        // The loop returns COMPLETED but a cancellation has since arrived; the post-turn check must skip the verification build and tear the session down.
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        BooleanSupplier cancelled = () -> true;

        GenerationOutcome outcome = generate(cancelled);

        assertThat(outcome.isAccepted()).isFalse();
        verify(verifier, never()).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
        verify(sandbox).destroySession(SESSION_ID);
    }

    /** A RuntimeException thrown by the agent loop still destroys the session (no container leak) and propagates. */
    @Test
    void thrownExceptionFromLoop_destroysSessionAndPropagates() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenThrow(new RuntimeException("model exploded"));

        assertThatThrownBy(() -> generate(() -> false)).isInstanceOf(RuntimeException.class).hasMessageContaining("model exploded");

        verify(sandbox, atLeastOnce()).destroySession(SESSION_ID);
        verify(verifier, never()).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    /** The structural-oracle seeder is invoked before verification on the accepted path, confirming the seeding step is wired into the loop. */
    @Test
    void acceptedPath_seedsStructuralOracleBeforeVerification() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(accepted());

        try (GenerationOutcome ignored = generate(() -> false)) {
            InOrder inOrder = inOrder(structuralOracleSeeder, verifier);
            inOrder.verify(structuralOracleSeeder).seedIfStructuralDiff(eq(sandbox), eq(SESSION_ID), eq(exercise));
            inOrder.verify(verifier).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    // --- Spec-fidelity critic integration: NON-BLOCKING and OBSERVABLE -------------------------------------------------------------------------------------------------------

    private static SpecFidelityReport reportWith(String requirement) {
        return new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, requirement, "no test covers it")));
    }

    /** The critic NEVER changes the verdict: an oracle-accepted exercise stays accepted even when the critic returns findings (the core non-blocking safety property). */
    @Test
    void criticFindings_neverFlipAcceptedToRejected() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any())).thenReturn(reportWith("CJK characters"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("an oracle-accepted exercise stays accepted regardless of critic findings").isTrue();
            assertThat(outcome.specFidelityReport().findings()).as("the advisory findings ride along on the outcome").extracting(SpecFidelityReport.Finding::requirement)
                    .containsExactly("CJK characters");
            // The critic did not trigger an extra retry on an accepted exercise.
            verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        }
    }

    /** On rejection with attempts remaining, the critic's findings are folded into the retry prompt alongside the authoritative rejection reason. */
    @Test
    void rejectedWithCriticFindings_foldsAdvisoryGapsIntoRetryPrompt() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(rejected("template passed a test"), accepted());
        when(specFidelityCritic.critique(any(), any(), any())).thenReturn(reportWith("emoji"), SpecFidelityReport.empty());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
            // settled
        }

        verify(agentLoopRunner, times(2)).run(anyString(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        String retryPrompt = promptCaptor.getAllValues().get(1);
        assertThat(retryPrompt).as("the retry prompt still carries the hard rejection").contains("rejected by the authoritative verifier").contains("template passed a test");
        assertThat(retryPrompt).as("and also the advisory spec-fidelity gap").contains("did NOT cause rejection").contains("emoji").contains("Add a test");
    }

    /** A critic that throws does NOT perturb the run: an accepted exercise stays accepted with an empty advisory report (graceful degradation at the orchestrator boundary). */
    @Test
    void criticThrows_runStillCompletesAndStaysAccepted() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any())).thenThrow(new RuntimeException("critic exploded"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("a critic failure never fails an oracle-accepted run").isTrue();
            assertThat(outcome.specFidelityReport().hasFindings()).as("a failed critic contributes no findings").isFalse();
        }
    }

    /**
     * The critic is fed the test names parsed from the produced problem statement's [task] bindings, so its coverage judgment sees which tests exist. Confirms the wiring that
     * turns [task](a,b) into the critic's test-name input.
     */
    @Test
    void critic_isFedTaskBoundTestNamesFromProblemStatement() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(accepted());
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("Intro.\n[task][Sort](test_sort,test_empty)\n[task][Edge](test_negative)");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> namesCaptor = ArgumentCaptor.forClass(List.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
            // settled
        }

        verify(specFidelityCritic).critique(anyString(), anyString(), namesCaptor.capture());
        assertThat(namesCaptor.getValue()).containsExactly("test_sort", "test_empty", "test_negative");
    }

    /** Unit-level check of the [task]-binding test-name extractor: dedup, trim, encounter order; empty for a blank statement. */
    @Test
    void extractTaskBoundTestNames_dedupesAndTrims() {
        assertThat(ExerciseGenerationOrchestrationService.extractTaskBoundTestNames("")).isEmpty();
        assertThat(ExerciseGenerationOrchestrationService.extractTaskBoundTestNames("[task][A]( t1 , t2 )\n[task][B](t2,t3)")).containsExactly("t1", "t2", "t3");
    }

    // --- Turn-0 workspace layout seeding (Fix #2) ----------------------------------------------------------------------------------------------------------------------------

    /** The seeded workspace layout is prepended to the first attempt's prompt as a delimited observation; the instructor brief still follows verbatim. */
    @Test
    void seededWorkspaceLayout_isPrependedToTheFirstPrompt() {
        when(workspace.probeWorkspaceLayout(any(), anyString())).thenReturn("--- ls -R solution template tests ---\nsolution:\nsrc");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(accepted());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
            // settled
        }

        verify(agentLoopRunner).run(anyString(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        String firstPrompt = promptCaptor.getValue();
        assertThat(firstPrompt).startsWith("=== INITIAL WORKSPACE (seeded; you do not need to re-list it) ===");
        assertThat(firstPrompt).contains("ls -R solution template tests").contains("=== END INITIAL WORKSPACE ===");
        assertThat(firstPrompt).as("the instructor brief still follows the seeded layout").endsWith("Build a bubble sort exercise.");
    }

    /** The seeded layout is prepended ONLY to the first attempt; a retry's prompt is rebuilt from the rejection report and must not re-inject the stale turn-0 snapshot. */
    @Test
    void seededLayout_isOnTheFirstPromptOnly_andNotReplayedOnRetry() {
        when(workspace.probeWorkspaceLayout(any(), anyString())).thenReturn("--- ls -R solution template tests ---\nsolution:\nsrc");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(rejected("template unexpectedly passed all tests"), accepted());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
            // settled
        }

        verify(agentLoopRunner, times(2)).run(anyString(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts.get(0)).as("attempt 1 carries the seeded layout").startsWith("=== INITIAL WORKSPACE");
        assertThat(prompts.get(1)).as("the retry is rebuilt from the rejection report and does NOT replay the stale turn-0 layout").doesNotContain("INITIAL WORKSPACE")
                .contains("template unexpectedly passed all tests");
    }

    /** When the probe yields nothing (empty workspace / probe failure), the first prompt is exactly the instructor brief — no empty observation block. */
    @Test
    void noProbeOutput_leavesTheFirstPromptUnchanged() {
        when(workspace.probeWorkspaceLayout(any(), anyString())).thenReturn("");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(accepted());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
            // settled
        }

        verify(agentLoopRunner).run(anyString(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        assertThat(promptCaptor.getValue()).isEqualTo("Build a bubble sort exercise.").doesNotContain("INITIAL WORKSPACE");
    }

    /** Unit-level check of the prepend helper: a layout block is delimited and the brief preserved; an empty/blank layout returns the brief unchanged. */
    @Test
    void prependWorkspaceLayout_delimitsLayoutAndPreservesBrief() {
        assertThat(ExerciseGenerationOrchestrationService.prependWorkspaceLayout("", "BRIEF")).isEqualTo("BRIEF");
        assertThat(ExerciseGenerationOrchestrationService.prependWorkspaceLayout("   ", "BRIEF")).isEqualTo("BRIEF");
        assertThat(ExerciseGenerationOrchestrationService.prependWorkspaceLayout(null, "BRIEF")).isEqualTo("BRIEF");

        String prepended = ExerciseGenerationOrchestrationService.prependWorkspaceLayout("LAYOUT", "BRIEF");
        assertThat(prepended).isEqualTo("=== INITIAL WORKSPACE (seeded; you do not need to re-list it) ===\nLAYOUT\n=== END INITIAL WORKSPACE ===\n\nBRIEF");
    }
}
