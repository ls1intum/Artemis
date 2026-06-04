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
 * Unit tests for the orchestrator's verifier-feedback retry loop, the highest-value behaviour of the agentic generation tier and previously covered only by the gated GPU E2E.
 * <p>
 * All collaborators are Mockito mocks, so the loop's control flow (retry on rejection, stop on acceptance, bound on attempts, cancellation short-circuit, session teardown on
 * error) is exercised deterministically with no Docker, LLM, or Hazelcast.
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
        // The orchestrator reads each repository back out as a RepositoryExtraction; default the mock to a successful, empty extraction so the verifier-feedback loop under test is
        // exercised without NPEs (the verifier itself is mocked, so the files are not inspected here).
        when(workspace.extractRepository(any(), anyString(), any())).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(java.util.Map.of(), false));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("PROBLEM STATEMENT");
        // The advisory critic is non-blocking; default it to no findings so the existing accept/reject-flow assertions are unaffected. Specific tests override it.
        when(specFidelityCritic.critique(any(), any(), any())).thenReturn(SpecFidelityReport.empty());

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

    /**
     * (a) A rejected first attempt feeds its verification report into the next prompt, and a subsequent accepted attempt yields an accepted outcome.
     * <p>
     * Mutation: replacing the rejection-driven retry with an unconditional {@code break} after attempt 1 makes both the prompt-feedback assertion and the acceptance assertion
     * fail (only one run, never accepted).
     */
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

    /**
     * (b) Acceptance on the first attempt runs the agent exactly once — no needless retry.
     * <p>
     * Mutation: looping a fixed number of times regardless of acceptance (dropping the {@code verification.accepted()} early break) makes the run-count assertion fail.
     */
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

    /**
     * (c) All attempts rejected runs exactly {@code MAX_GENERATION_ATTEMPTS} times and returns a non-accepted outcome.
     * <p>
     * Mutation: an unconditional {@code break} after attempt 1 makes the run-count assertion fail (1 != 3); raising the bound makes it fail the other way.
     */
    @Test
    void allAttemptsRejected_runsMaxAttemptsAndReturnsNotAccepted() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(rejected("still failing"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("an exercise rejected on every attempt is not accepted").isFalse();
            assertThat(outcome.verification()).isNotNull();
        }

        verify(agentLoopRunner, times(MAX_GENERATION_ATTEMPTS)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(MAX_GENERATION_ATTEMPTS)).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * (d) A CANCELLED loop result short-circuits before verification and destroys the session.
     * <p>
     * Mutation: removing the CANCELLED branch (falling through to verify) makes the {@code verify(...)} never-called assertion fail; removing the teardown makes the destroySession
     * assertion fail.
     */
    @Test
    void cancelledLoopResult_skipsVerificationAndDestroysSession() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));

        GenerationOutcome outcome = generate(() -> false);

        assertThat(outcome.isAccepted()).isFalse();
        verify(verifier, never()).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
        verify(sandbox).destroySession(SESSION_ID);
    }

    /**
     * (d') A cancellation flag flipping true between the loop turn and verification short-circuits before verification and destroys the session.
     * <p>
     * Mutation: removing the between-turn {@code cancelled.getAsBoolean()} check makes the verify-never assertion fail (verification would run on a cancelled job).
     */
    @Test
    void cancellationBetweenTurns_skipsVerificationAndDestroysSession() {
        // The loop returns COMPLETED (the mocked runner ignores the cancellation supplier), but a cancellation has since arrived; the orchestrator's post-turn check must then skip
        // the verification build and tear the session down.
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        BooleanSupplier cancelled = () -> true;

        GenerationOutcome outcome = generate(cancelled);

        assertThat(outcome.isAccepted()).isFalse();
        verify(verifier, never()).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
        verify(sandbox).destroySession(SESSION_ID);
    }

    /**
     * (e) A RuntimeException thrown by the agent loop still destroys the session (no container leak) and propagates.
     * <p>
     * Mutation: removing the {@code destroyQuietly} call in the catch block makes the destroySession assertion fail.
     */
    @Test
    void thrownExceptionFromLoop_destroysSessionAndPropagates() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenThrow(new RuntimeException("model exploded"));

        assertThatThrownBy(() -> generate(() -> false)).isInstanceOf(RuntimeException.class).hasMessageContaining("model exploded");

        verify(sandbox, atLeastOnce()).destroySession(SESSION_ID);
        verify(verifier, never()).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Sanity: the structural-oracle seeder is invoked before verification on a normal (accepted) path, confirming the additive seeding step is wired into the loop and not skipped.
     * <p>
     * Mutation: deleting the {@code structuralOracleSeeder.seedIfStructuralDiff(...)} call makes this assertion fail.
     */
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

    /**
     * The critic NEVER changes the accept/reject verdict: an exercise the differential oracle ACCEPTS stays accepted even when the critic returns findings. This is the core
     * non-blocking safety property — a critic false positive can only add an advisory note, never reject a sound exercise.
     * <p>
     * Mutation: making acceptance consult {@code specFidelityReport.hasFindings()} (e.g. {@code accepted && !report.hasFindings()}) makes this assertion fail.
     */
    @Test
    void criticFindings_neverFlipAcceptedToRejected() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any())).thenReturn(reportWith("CJK characters"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("an oracle-accepted exercise stays accepted regardless of critic findings").isTrue();
            assertThat(outcome.specFidelityReport().findings()).as("the advisory findings ride along on the outcome").extracting(SpecFidelityReport.Finding::requirement)
                    .containsExactly("CJK characters");
            // Acceptance ran the agent exactly once: the critic did not trigger an extra retry on an accepted exercise.
            verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        }
    }

    /**
     * When verification rejects and attempts remain, the critic's findings are folded into the next retry prompt so the agent can add the missing test, ALONGSIDE the authoritative
     * rejection reason. The critic never causes the rejection — the verifier did — but its advice rides the same retry.
     * <p>
     * Mutation: dropping {@code renderForRetryPrompt(report)} from the retry prompt makes the "Add a test" assertion fail.
     */
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

    /**
     * A critic that throws does NOT perturb the run: an accepted exercise stays accepted and the outcome simply carries an empty advisory report. Graceful degradation at the
     * orchestrator boundary, on top of the critic's own internal graceful skip.
     * <p>
     * Mutation: removing the try/catch around {@code runSpecFidelityCritic} lets the exception propagate and the accepted outcome is never produced — this assertion fails.
     */
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
     * Per-attempt turn-budget telemetry is recorded: one entry per agent run, in order, so verifier-feedback thrash (later attempts pinned at the cap) is observable after the
     * fact.
     * <p>
     * Mutation: not appending {@code loopResult.turns()} per attempt makes the size/order assertion fail.
     */
    @Test
    void attemptTurnCounts_recordOneEntryPerAttemptInOrder() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 12, "a"),
                new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 30, "b"), new AgentLoopResult(AgentLoopResult.Status.BUDGET_EXHAUSTED, 30, "c"));
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(rejected("still failing"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.attemptTurnCounts()).as("one turn-count per attempt, in order").containsExactly(12, 30, 30);
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
}
