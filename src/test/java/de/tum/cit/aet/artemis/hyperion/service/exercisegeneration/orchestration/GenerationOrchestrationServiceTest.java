package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StructuralOracleSeedingService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationRequest;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;

/**
 * Unit tests for the orchestrator's verifier-feedback retry loop. All collaborators are Mockito mocks, so the loop's control flow (retry on rejection, stop on acceptance, bound on
 * attempts, cancellation short-circuit, session teardown on error) is exercised deterministically with no Docker, LLM, or Hazelcast.
 */
class GenerationOrchestrationServiceTest {

    private static final int MAX_GENERATION_ATTEMPTS = 3;

    private InteractiveSandbox sandbox;

    private AgentLoopRunner agentLoopRunner;

    private DifferentialVerificationService verifier;

    private StructuralOracleSeedingService structuralOracleSeeder;

    private SpecFidelityCriticService specFidelityCritic;

    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    private GenerationWorkspaceService workspace;

    private AgentSystemPromptService systemPromptService;

    private GenerationJobService jobService;

    private GenerationOrchestrationService service;

    private ProgrammingExercise exercise;

    private User user;

    private static final String JOB_ID = "job-1";

    private static final String SESSION_ID = "session-abc";

    /** The fresh sandbox session the authoritative verification runs in (distinct from the agent-loop session). */
    private static final String VERIFY_SESSION_ID = "verify-session-xyz";

    @BeforeEach
    void setUp() {
        sandbox = mock(InteractiveSandbox.class);
        workspace = mock(GenerationWorkspaceService.class);
        agentLoopRunner = mock(AgentLoopRunner.class);
        verifier = mock(DifferentialVerificationService.class);
        systemPromptService = mock(AgentSystemPromptService.class);
        structuralOracleSeeder = mock(StructuralOracleSeedingService.class);
        specFidelityCritic = mock(SpecFidelityCriticService.class);
        jobService = mock(GenerationJobService.class);

        when(sandbox.createSession(any())).thenReturn(SESSION_ID);
        // The authoritative verify copies the loop workspace into a fresh session; a valid (empty) tar keeps the copy step working for every verify-reaching test.
        when(sandbox.copyOut(anyString(), anyString())).thenAnswer(invocation -> emptyTar());
        when(systemPromptService.build(any(), any())).thenReturn("SYSTEM_PROMPT");
        // Default to a successful, empty extraction (the verifier is mocked, so files are not inspected here).
        when(workspace.extractRepository(any(), anyString(), any())).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of(), false));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("PROBLEM STATEMENT");
        // Default the advisory critic to no findings; specific tests override it.
        when(specFidelityCritic.critique(any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());
        // renderForRetryPrompt is a pure renderer; delegate to the real impl so the retry prompt is folded exactly as in production.
        SpecFidelityCriticService renderingDelegate = new SpecFidelityCriticService(null, new ObjectMapper());
        when(specFidelityCritic.renderForRetryPrompt(any())).thenAnswer(invocation -> renderingDelegate.renderForRetryPrompt(invocation.getArgument(0)));

        testCaseRepository = mock(ProgrammingExerciseTestCaseTestRepository.class);
        // Default: the exercise has no persisted graded tests (GENERATE and most ADAPT tests); the total-wipe baseline is then empty and the gate inert.
        when(testCaseRepository.findByExerciseId(anyLong())).thenReturn(Set.of());
        service = newService();

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(null);
        when(exercise.getProgrammingLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        user = mock(User.class);
        when(user.getId()).thenReturn(7L);
    }

    /** Builds the orchestration service with all collaborators mocked and the sandbox/test-case repository present. */
    private GenerationOrchestrationService newService() {
        return new GenerationOrchestrationService(Optional.of(sandbox), workspace, agentLoopRunner, verifier, systemPromptService, structuralOracleSeeder, specFidelityCritic,
                jobService, Optional.of(testCaseRepository), 100);
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
        return service.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, cancelled, null, null);
    }

    /** A rejected first attempt feeds its verification report into the next prompt, and a subsequent accepted attempt yields an accepted outcome. */
    @Test
    void rejectedThenAccepted_feedsReportIntoNextPromptAndAccepts() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(rejected("template unexpectedly passed all tests"), accepted());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("the second, accepted attempt yields an accepted outcome").isTrue();
        }

        verify(agentLoopRunner, times(2)).run(anyString(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts.get(0)).as("the first prompt is the instructor brief").isEqualTo("Build a bubble sort exercise.");
        assertThat(prompts.get(1)).as("the second prompt carries the verifier's rejection report so the agent can fix exactly those issues")
                .contains("template unexpectedly passed all tests").contains("rejected by the differential verifier");
    }

    /** Acceptance on the first attempt runs the agent exactly once — no needless retry. */
    @Test
    void acceptedOnFirstAttempt_runsAgentExactlyOnce() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(1)).verify(any(), anyString(), any(), any(VerificationRequest.class));
    }

    /** ADAPT captures the exercise's persisted graded test names and hands them to the verifier as the adapt total-wipe baseline. */
    @Test
    void adaptMode_passesThePersistedGradedTestNamesAsTheTotalWipeBaseline() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(testCaseRepository.findByExerciseId(42L)).thenReturn(Set.of(testCase("evictsLeastRecentlyUsed"), testCase("capacityIsRespected")));

        try (GenerationOutcome outcome = service.generate(exercise, user, "Tighten the eviction test.", JOB_ID, GenerationMode.ADAPT, () -> false, null, null)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        ArgumentCaptor<VerificationRequest> requestCaptor = ArgumentCaptor.forClass(VerificationRequest.class);
        verify(verifier).verify(any(), anyString(), any(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().baselineGradedTestNames()).as("ADAPT hands the persisted graded test names to the total-wipe gate")
                .containsExactlyInAnyOrder("evictsLeastRecentlyUsed", "capacityIsRespected");
    }

    /** GENERATE has no pre-adapt baseline: it passes an empty total-wipe baseline and never reads the persisted test cases. */
    @Test
    void generateMode_passesAnEmptyTotalWipeBaselineAndDoesNotQueryPersistedTests() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        ArgumentCaptor<VerificationRequest> requestCaptor = ArgumentCaptor.forClass(VerificationRequest.class);
        verify(verifier).verify(any(), anyString(), any(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().baselineGradedTestNames()).as("GENERATE has no pre-adapt baseline, so the total-wipe gate is inert").isEmpty();
        verify(testCaseRepository, never()).findByExerciseId(anyLong());
    }

    private static ProgrammingExerciseTestCase testCase(String name) {
        ProgrammingExerciseTestCase testCase = new ProgrammingExerciseTestCase();
        testCase.setTestName(name);
        return testCase;
    }

    /** All attempts rejected runs exactly {@code MAX_GENERATION_ATTEMPTS} times and returns a non-accepted outcome. */
    @Test
    void allAttemptsRejected_runsMaxAttemptsAndReturnsNotAccepted() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(rejected("still failing"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("an exercise rejected on every attempt is not accepted").isFalse();
        }

        verify(agentLoopRunner, times(MAX_GENERATION_ATTEMPTS)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(MAX_GENERATION_ATTEMPTS)).verify(any(), anyString(), any(), any(VerificationRequest.class));
    }

    /** A CANCELLED loop result short-circuits before verification and destroys the session. */
    @Test
    void cancelledLoopResult_skipsVerificationAndDestroysSession() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));

        GenerationOutcome outcome = generate(() -> false);

        assertThat(outcome.isAccepted()).isFalse();
        verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class));
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
        verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class));
        verify(sandbox).destroySession(SESSION_ID);
    }

    /** A RuntimeException thrown by the agent loop still destroys the session (no container leak) and propagates. */
    @Test
    void thrownExceptionFromLoop_destroysSessionAndPropagates() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenThrow(new RuntimeException("model exploded"));

        assertThatThrownBy(() -> generate(() -> false)).isInstanceOf(RuntimeException.class).hasMessageContaining("model exploded");

        verify(sandbox, atLeastOnce()).destroySession(SESSION_ID);
        verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class));
    }

    /** The structural-oracle seeder is invoked before verification on the accepted path, confirming the seeding step is wired into the loop. */
    @Test
    void acceptedPath_seedsStructuralOracleBeforeVerification() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());

        try (GenerationOutcome ignored = generate(() -> false)) {
            InOrder inOrder = inOrder(structuralOracleSeeder, verifier);
            inOrder.verify(structuralOracleSeeder).seedIfStructuralDiff(eq(sandbox), eq(SESSION_ID), eq(exercise));
            inOrder.verify(verifier).verify(any(), anyString(), any(), any(VerificationRequest.class));
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
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any())).thenReturn(reportWith("CJK characters"));

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
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(rejected("template passed a test"), accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any())).thenReturn(reportWith("emoji"), SpecFidelityReport.empty());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
        }

        verify(agentLoopRunner, times(2)).run(anyString(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        String retryPrompt = promptCaptor.getAllValues().get(1);
        assertThat(retryPrompt).as("the retry prompt still carries the hard rejection").contains("rejected by the differential verifier").contains("template passed a test");
        assertThat(retryPrompt).as("and also the advisory spec-fidelity gap").contains("did NOT cause rejection").contains("emoji").contains("Add a test");
    }

    /** A critic that throws does NOT perturb the run: an accepted exercise stays accepted with an empty advisory report (graceful degradation at the orchestrator boundary). */
    @Test
    void criticThrows_runStillCompletesAndStaysAccepted() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any())).thenThrow(new RuntimeException("critic exploded"));

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
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("Intro.\n[task][Sort](test_sort,test_empty)\n[task][Edge](test_negative)");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> namesCaptor = ArgumentCaptor.forClass(List.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
        }

        verify(specFidelityCritic).critique(anyString(), anyString(), namesCaptor.capture(), any());
        assertThat(namesCaptor.getValue()).containsExactly("test_sort", "test_empty", "test_negative");
    }

    /** Unit-level check of the [task]-binding test-name extractor: dedup, trim, encounter order; empty for a blank statement. */
    @Test
    void extractTaskBoundTestNames_dedupesAndTrims() {
        assertThat(GenerationOrchestrationService.extractTaskBoundTestNames("")).isEmpty();
        assertThat(GenerationOrchestrationService.extractTaskBoundTestNames("[task][A]( t1 , t2 )\n[task][B](t2,t3)")).containsExactly("t1", "t2", "t3");
    }

    // --- Turn-0 workspace layout seeding (Fix #2) ----------------------------------------------------------------------------------------------------------------------------

    /** The seeded workspace layout is prepended to the first attempt's prompt as a delimited observation; the instructor brief still follows verbatim. */
    @Test
    void seededWorkspaceLayout_isPrependedToTheFirstPrompt() {
        when(workspace.probeWorkspaceLayout(any(), anyString())).thenReturn("--- ls -R solution template tests ---\nsolution:\nsrc");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
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
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(rejected("template unexpectedly passed all tests"), accepted());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
        }

        verify(agentLoopRunner, times(2)).run(anyString(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts.get(0)).as("attempt 1 carries the seeded layout").startsWith("=== INITIAL WORKSPACE");
        assertThat(prompts.get(1)).as("the retry is rebuilt from the rejection report and does NOT replay the stale turn-0 layout").doesNotContain("INITIAL WORKSPACE")
                .contains("template unexpectedly passed all tests");
    }

    /** Unit-level check of the prepend helper: a layout block is delimited and the brief preserved; an empty/blank layout returns the brief unchanged. */
    @Test
    void prependWorkspaceLayout_delimitsLayoutAndPreservesBrief() {
        assertThat(GenerationOrchestrationService.prependWorkspaceLayout("", "BRIEF")).isEqualTo("BRIEF");
        assertThat(GenerationOrchestrationService.prependWorkspaceLayout("   ", "BRIEF")).isEqualTo("BRIEF");
        assertThat(GenerationOrchestrationService.prependWorkspaceLayout(null, "BRIEF")).isEqualTo("BRIEF");

        String prepended = GenerationOrchestrationService.prependWorkspaceLayout("LAYOUT", "BRIEF");
        assertThat(prepended).isEqualTo("=== INITIAL WORKSPACE (seeded; you do not need to re-list it) ===\nLAYOUT\n=== END INITIAL WORKSPACE ===\n\nBRIEF");
    }

    // --- Fresh-session authoritative verification (forgery concurrency gap)
    // --------------------------------------------------------------------------------------------------------

    /** A valid empty tar so the fresh-session workspace copy has something to read. */
    private static TarArchiveInputStream emptyTar() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            // No entries: a valid, empty archive.
            tar.finish();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
    }

    /**
     * The sole-acceptance verification runs in a FRESH sandbox session (a new container that never hosted the agent loop), against the produced tree copied over from the loop
     * session, and that fresh session is always destroyed — so no agent-spawned process or planted file can follow the verdict.
     */
    @Test
    void authoritativeVerify_runsInAFreshSession_copiesTheWorkspaceIn_andAlwaysDestroysIt() {
        when(sandbox.createSession(any())).thenReturn(SESSION_ID, VERIFY_SESSION_ID);
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        // The authoritative verify decided on the FRESH session, never the agent-loop session.
        verify(verifier).verify(eq(sandbox), eq(VERIFY_SESSION_ID), eq(exercise), any(VerificationRequest.class));
        verify(verifier, never()).verify(eq(sandbox), eq(SESSION_ID), any(), any(VerificationRequest.class));
        // The exact produced tree was copied from the loop session into the fresh session.
        verify(sandbox).copyOut(SESSION_ID, GenerationWorkspaceService.WORKSPACE);
        verify(sandbox).copyIn(eq(VERIFY_SESSION_ID), eq("/"), any());
        // The fresh verification session is always torn down (the loop session stays open on the returned outcome and is destroyed by the caller's close()).
        verify(sandbox).destroySession(VERIFY_SESSION_ID);
    }

    /**
     * A BUDGET_EXHAUSTED loop is still verified authoritatively in a fresh session (fail-closed to not-accepted when the verifier rejects), and the fresh session is destroyed. The
     * loop session is not torn down inside generate — the outcome carries it for the caller to close.
     */
    @Test
    void budgetExhaustedLoop_stillRunsAuthoritativeFreshSessionVerify_andDestroysTheFreshSession() {
        when(sandbox.createSession(any())).thenReturn(SESSION_ID, VERIFY_SESSION_ID);
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any()))
                .thenReturn(new AgentLoopResult(AgentLoopResult.Status.BUDGET_EXHAUSTED, 100, "ran out of turns"));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(rejected("template passed a graded test"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
            assertThat(outcome.isAccepted()).as("a rejected budget-exhausted run is not accepted").isFalse();
        }

        verify(verifier, atLeastOnce()).verify(eq(sandbox), eq(VERIFY_SESSION_ID), any(), any(VerificationRequest.class));
        verify(sandbox, atLeastOnce()).destroySession(VERIFY_SESSION_ID);
    }

    // --- Reuse of verification-time extractions at persist (no double sandbox read)
    // ------------------------------------------------------------------------------------------------

    /**
     * The files verification already extracted for its integrity gates ride the outcome, so persist reuses them: {@code producedFiles}/{@code producedProblemStatement} return the
     * captured extraction and never trigger the lazy sandbox re-read. Each repository is extracted exactly once (during verification).
     */
    @Test
    void acceptedOutcome_reusesVerificationExtractions_soPersistDoesNotReReadTheSandbox() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(workspace.extractRepository(sandbox, SESSION_ID, RepositoryType.TESTS))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("tests/T.java", "t"), false));
        when(workspace.extractRepository(sandbox, SESSION_ID, RepositoryType.TEMPLATE))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("template/M.java", "m"), false));
        when(workspace.extractRepository(sandbox, SESSION_ID, RepositoryType.SOLUTION))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("solution/S.java", "s"), false));
        when(workspace.extractProblemStatement(sandbox, SESSION_ID)).thenReturn("# Title\n\nStatement");

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
            // What persist consumes — served from the captured verification-time extraction.
            assertThat(outcome.producedFiles(RepositoryType.TESTS)).containsExactlyEntriesOf(Map.of("tests/T.java", "t"));
            assertThat(outcome.producedFiles(RepositoryType.TEMPLATE)).containsExactlyEntriesOf(Map.of("template/M.java", "m"));
            assertThat(outcome.producedFiles(RepositoryType.SOLUTION)).containsExactlyEntriesOf(Map.of("solution/S.java", "s"));
            assertThat(outcome.producedProblemStatement()).isEqualTo("# Title\n\nStatement");
        }

        // Each repo read back exactly once (for the integrity gates); the lazy re-read path is never taken.
        verify(workspace, times(1)).extractRepository(sandbox, SESSION_ID, RepositoryType.TESTS);
        verify(workspace, times(1)).extractRepository(sandbox, SESSION_ID, RepositoryType.TEMPLATE);
        verify(workspace, times(1)).extractRepository(sandbox, SESSION_ID, RepositoryType.SOLUTION);
        verify(workspace, never()).extractRepositoryFiles(any(), anyString(), any());
    }
}
