package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.CrossCheckService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.CrossCheckVerdict;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StructuralOracleSeedingService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;

/**
 * Unit tests for the orchestrator's verifier-feedback retry loop. All collaborators are Mockito mocks, so the loop's control flow (retry on rejection, stop on acceptance, bound on
 * attempts, cancellation short-circuit, session teardown on error) is exercised deterministically with no Docker, LLM, or Hazelcast.
 */
class ExerciseGenerationOrchestrationServiceTest {

    private static final int MAX_GENERATION_ATTEMPTS = 3;

    private InteractiveSandbox sandbox;

    private AgentLoopRunner agentLoopRunner;

    private DifferentialVerificationService verifier;

    private StructuralOracleSeedingService structuralOracleSeeder;

    private SpecFidelityCriticService specFidelityCritic;

    private IndependentExaminerService independentExaminer;

    private CrossCheckService crossCheckService;

    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    private GenerationWorkspaceService workspace;

    private AgentSystemPromptService systemPromptService;

    private ExerciseGenerationJobService jobService;

    private LLMTokenUsageService llmTokenUsageService;

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
        verifier = mock(DifferentialVerificationService.class);
        systemPromptService = mock(AgentSystemPromptService.class);
        structuralOracleSeeder = mock(StructuralOracleSeedingService.class);
        specFidelityCritic = mock(SpecFidelityCriticService.class);
        independentExaminer = mock(IndependentExaminerService.class);
        crossCheckService = mock(CrossCheckService.class);
        jobService = mock(ExerciseGenerationJobService.class);
        llmTokenUsageService = mock(LLMTokenUsageService.class);

        when(sandbox.createSession(any())).thenReturn(SESSION_ID);
        when(systemPromptService.build(any(), any())).thenReturn("SYSTEM_PROMPT");
        // Default to a successful, empty extraction (the verifier is mocked, so files are not inspected here).
        when(workspace.extractRepository(any(), anyString(), any())).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of(), false));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("PROBLEM STATEMENT");
        // Default the advisory critic to no findings; specific tests override it.
        when(specFidelityCritic.critique(any(), any(), any())).thenReturn(SpecFidelityReport.empty());
        // renderForRetryPrompt is a pure renderer; delegate to the real impl so the retry prompt is folded exactly as in production.
        SpecFidelityCriticService renderingDelegate = new SpecFidelityCriticService(null, new ObjectMapper());
        when(specFidelityCritic.renderForRetryPrompt(any())).thenAnswer(invocation -> renderingDelegate.renderForRetryPrompt(invocation.getArgument(0)));

        testCaseRepository = mock(ProgrammingExerciseTestCaseTestRepository.class);
        // Default: the exercise has no persisted graded tests (GENERATE and most ADAPT tests); the total-wipe baseline is then empty and the gate inert.
        when(testCaseRepository.findByExerciseId(anyLong())).thenReturn(Set.of());
        // Cross-check default: ADVISORY-ENABLED, reject-off (matches the production default). The generic control-flow tests must not NPE on the now-live path, so default the
        // examiner to no suite and the cross-check to CONSISTENT (no contradiction -> no finding, no retry). Specific tests override these.
        when(independentExaminer.authorShadowSuite(any(), any(), any(), any(), any(), any())).thenReturn(Map.of());
        when(crossCheckService.runAgainstShadowSuite(any(), anyString(), any(), any())).thenReturn(consistent());
        service = crossCheckService(true, false);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(null);
        when(exercise.getProgrammingLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        user = mock(User.class);
        when(user.getId()).thenReturn(7L);
    }

    /** Builds the service with the cross-check enabled/disabled and the reject-on-contradiction flag set, allowlisting JAVA (the exercise's language). */
    private ExerciseGenerationOrchestrationService crossCheckService(boolean crossCheckEnabled, boolean rejectOnContradiction) {
        return new ExerciseGenerationOrchestrationService(Optional.of(sandbox), workspace, agentLoopRunner, verifier, systemPromptService, structuralOracleSeeder,
                specFidelityCritic, independentExaminer, crossCheckService, jobService, llmTokenUsageService, Optional.of(testCaseRepository), 100, crossCheckEnabled,
                Set.of(ProgrammingLanguage.JAVA), rejectOnContradiction);
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
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(rejected("template unexpectedly passed all tests"), accepted());

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
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(1)).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    /** ADAPT captures the exercise's persisted graded test names and hands them to the verifier as the adapt total-wipe baseline. */
    @Test
    @SuppressWarnings("unchecked")
    void adaptMode_passesThePersistedGradedTestNamesAsTheTotalWipeBaseline() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());
        when(testCaseRepository.findByExerciseId(42L)).thenReturn(Set.of(testCase("evictsLeastRecentlyUsed"), testCase("capacityIsRespected")));

        try (GenerationOutcome outcome = service.generate(exercise, user, "Tighten the eviction test.", JOB_ID, GenerationMode.ADAPT, () -> false, null, null)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        ArgumentCaptor<Set<String>> baselineCaptor = ArgumentCaptor.forClass(Set.class);
        verify(verifier).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), baselineCaptor.capture(), anyBoolean());
        assertThat(baselineCaptor.getValue()).as("ADAPT hands the persisted graded test names to the total-wipe gate").containsExactlyInAnyOrder("evictsLeastRecentlyUsed",
                "capacityIsRespected");
    }

    /** GENERATE has no pre-adapt baseline: it passes an empty total-wipe baseline and never reads the persisted test cases. */
    @Test
    @SuppressWarnings("unchecked")
    void generateMode_passesAnEmptyTotalWipeBaselineAndDoesNotQueryPersistedTests() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        ArgumentCaptor<Set<String>> baselineCaptor = ArgumentCaptor.forClass(Set.class);
        verify(verifier).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), baselineCaptor.capture(), anyBoolean());
        assertThat(baselineCaptor.getValue()).as("GENERATE has no pre-adapt baseline, so the total-wipe gate is inert").isEmpty();
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
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(rejected("still failing"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("an exercise rejected on every attempt is not accepted").isFalse();
        }

        verify(agentLoopRunner, times(MAX_GENERATION_ATTEMPTS)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(MAX_GENERATION_ATTEMPTS)).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    /** A CANCELLED loop result short-circuits before verification and destroys the session. */
    @Test
    void cancelledLoopResult_skipsVerificationAndDestroysSession() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));

        GenerationOutcome outcome = generate(() -> false);

        assertThat(outcome.isAccepted()).isFalse();
        verify(verifier, never()).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
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
        verify(verifier, never()).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(sandbox).destroySession(SESSION_ID);
    }

    /** A RuntimeException thrown by the agent loop still destroys the session (no container leak) and propagates. */
    @Test
    void thrownExceptionFromLoop_destroysSessionAndPropagates() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenThrow(new RuntimeException("model exploded"));

        assertThatThrownBy(() -> generate(() -> false)).isInstanceOf(RuntimeException.class).hasMessageContaining("model exploded");

        verify(sandbox, atLeastOnce()).destroySession(SESSION_ID);
        verify(verifier, never()).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    /** The structural-oracle seeder is invoked before verification on the accepted path, confirming the seeding step is wired into the loop. */
    @Test
    void acceptedPath_seedsStructuralOracleBeforeVerification() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());

        try (GenerationOutcome ignored = generate(() -> false)) {
            InOrder inOrder = inOrder(structuralOracleSeeder, verifier);
            inOrder.verify(structuralOracleSeeder).seedIfStructuralDiff(eq(sandbox), eq(SESSION_ID), eq(exercise));
            inOrder.verify(verifier).verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
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
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());
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
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(rejected("template passed a test"), accepted());
        when(specFidelityCritic.critique(any(), any(), any())).thenReturn(reportWith("emoji"), SpecFidelityReport.empty());

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
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());
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
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("Intro.\n[task][Sort](test_sort,test_empty)\n[task][Edge](test_negative)");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> namesCaptor = ArgumentCaptor.forClass(List.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
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
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());

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
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(rejected("template unexpectedly passed all tests"), accepted());

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
        assertThat(ExerciseGenerationOrchestrationService.prependWorkspaceLayout("", "BRIEF")).isEqualTo("BRIEF");
        assertThat(ExerciseGenerationOrchestrationService.prependWorkspaceLayout("   ", "BRIEF")).isEqualTo("BRIEF");
        assertThat(ExerciseGenerationOrchestrationService.prependWorkspaceLayout(null, "BRIEF")).isEqualTo("BRIEF");

        String prepended = ExerciseGenerationOrchestrationService.prependWorkspaceLayout("LAYOUT", "BRIEF");
        assertThat(prepended).isEqualTo("=== INITIAL WORKSPACE (seeded; you do not need to re-list it) ===\nLAYOUT\n=== END INITIAL WORKSPACE ===\n\nBRIEF");
    }

    // --- Decorrelated cross-check: ADDITIVE, never loosens accepted=, config-flag hard gate -------------------------------------------------------------------------

    private static final Map<String, String> SHADOW_SUITE = Map.of("test/de/test/LRUCacheTest.java", "class LRUCacheTest {}");

    private static CrossCheckVerdict contradiction() {
        return new CrossCheckVerdict(CrossCheckVerdict.Status.CONTRADICTION, List.of("evictsLeastRecentlyUsedInsertionOrder"), "solution fails 1 of 1");
    }

    private static CrossCheckVerdict consistent() {
        return new CrossCheckVerdict(CrossCheckVerdict.Status.CONSISTENT, List.of(), "all pass");
    }

    /**
     * With the PRODUCTION default (advisory-enabled, reject-off) the examiner is seeded from the artifacts the agent ACTUALLY produced — {@code producedTemplate.files()} and
     * {@code producedTests.files()}, never the solution and never the pre-generation scaffold. Seeding from the produced artifacts is what makes the shadow suite compile against
     * the
     * real API (effective, not a silent no-op); seeding the solution would break decorrelation.
     */
    @Test
    @SuppressWarnings("unchecked")
    void crossCheck_authorsShadowSuiteAgainstTheProducedTemplateAndTests() {
        Map<String, String> producedTemplate = Map.of("src/A.java", "class A{}");
        Map<String, String> producedTests = Map.of("pom.xml", "<project/>");
        Map<String, String> producedSolution = Map.of("src/A.java", "class A{int x;}");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.TEMPLATE))).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(producedTemplate, false));
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.TESTS))).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(producedTests, false));
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.SOLUTION))).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(producedSolution, false));
        when(independentExaminer.authorShadowSuite(any(), any(), any(), any(), any(), any())).thenReturn(SHADOW_SUITE);

        try (GenerationOutcome ignored = generate(() -> false)) {
        }

        ArgumentCaptor<Map<String, String>> templateCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> testsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(independentExaminer).authorShadowSuite(eq(exercise), templateCaptor.capture(), testsCaptor.capture(), any(), any(), any());
        // Equality to the PRODUCED template (which differs from producedSolution) is the positive decorrelation proof: the examiner sees the template, never the solution.
        assertThat(templateCaptor.getValue()).as("the examiner is seeded from the PRODUCED template, not the stale pre-generation scaffold").isEqualTo(producedTemplate);
        assertThat(testsCaptor.getValue()).as("the examiner is seeded from the PRODUCED tests harness").isEqualTo(producedTests);
    }

    /**
     * PRODUCTION default (advisory-enabled, reject-off): a contradiction stays ACCEPTED, adds an advisory CONTRACT_CONTRADICTION finding, does NOT hard-block, and does NOT trigger
     * a
     * retry. Uses the {@code setUp} default service to prove the default itself is advisory-enabled (only overriding the cross-check verdict to a contradiction).
     */
    @Test
    void crossCheckAdvisoryByDefault_contradictionStaysAcceptedAddsFindingNoRetryNoBlock() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());
        when(independentExaminer.authorShadowSuite(any(), any(), any(), any(), any(), any())).thenReturn(SHADOW_SUITE);
        when(crossCheckService.runAgainstShadowSuite(any(), anyString(), any(), any())).thenReturn(contradiction());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("the differential acceptance is never loosened by the advisory default").isTrue();
            assertThat(outcome.isHardBlockedByCrossCheck()).as("the advisory default never hard-blocks").isFalse();
            assertThat(outcome.crossCheckVerdict().isContradiction()).isTrue();
            assertThat(outcome.specFidelityReport().findings()).extracting(SpecFidelityReport.Finding::kind).contains(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION);
        }
        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    /**
     * The off-switch still works: with the master flag explicitly OFF the cross-check never runs — no examiner agent, no cross-check — and the outcome carries no cross-check
     * result.
     */
    @Test
    void crossCheckCanBeExplicitlyDisabled() {
        service = crossCheckService(false, false);
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
            assertThat(outcome.crossCheckVerdict()).as("the cross-check did not run with the flag explicitly off").isNull();
            assertThat(outcome.isHardBlockedByCrossCheck()).isFalse();
        }
        verify(independentExaminer, never()).authorShadowSuite(any(), any(), any(), any(), any(), any());
        verify(crossCheckService, never()).runAgainstShadowSuite(any(), anyString(), any(), any());
    }

    /** Enabled + reject-on-contradiction, attempts remaining: the contradiction retries with the examiner feedback in the prompt; a subsequent consistent run persists cleanly. */
    @Test
    void crossCheckContradiction_rejectMode_retriesWithExaminerFeedbackThenClears() {
        service = crossCheckService(true, true);
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());
        when(independentExaminer.authorShadowSuite(any(), any(), any(), any(), any(), any())).thenReturn(SHADOW_SUITE);
        when(crossCheckService.runAgainstShadowSuite(any(), anyString(), any(), any())).thenReturn(contradiction(), consistent());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
            assertThat(outcome.isHardBlockedByCrossCheck()).as("the second, consistent attempt clears the block").isFalse();
        }
        verify(agentLoopRunner, times(2)).run(anyString(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        assertThat(promptCaptor.getAllValues().get(1)).as("the retry prompt names the contradicted behaviour").contains("contract contradiction")
                .contains("evictsLeastRecentlyUsedInsertionOrder");
    }

    /**
     * Enabled + reject-on-contradiction, attempts exhausted: the outcome is differential-ACCEPTED yet HARD-BLOCKED, so the task service routes it to review, not a silent persist.
     */
    @Test
    void crossCheckContradiction_rejectMode_exhausted_isAcceptedButHardBlocked() {
        service = crossCheckService(true, true);
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(accepted());
        when(independentExaminer.authorShadowSuite(any(), any(), any(), any(), any(), any())).thenReturn(SHADOW_SUITE);
        when(crossCheckService.runAgainstShadowSuite(any(), anyString(), any(), any())).thenReturn(contradiction());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("the differential remains the sole author of acceptance").isTrue();
            assertThat(outcome.isHardBlockedByCrossCheck()).as("a proven, unresolved contradiction hard-blocks persistence").isTrue();
            assertThat(outcome.specFidelityReport().findings()).extracting(SpecFidelityReport.Finding::kind).contains(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION);
        }
        verify(agentLoopRunner, times(MAX_GENERATION_ATTEMPTS)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }
}
