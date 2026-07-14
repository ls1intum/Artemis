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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
        when(systemPromptService.build(any(), any())).thenReturn("SYSTEM_PROMPT");
        when(workspace.extractRepository(any(), anyString(), any(), any())).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of(), false));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("PROBLEM STATEMENT");
        when(workspace.seedWorkspace(any(), anyString(), any(), any())).thenReturn(new GenerationWorkspaceService.WorkspaceSeed(Map.of(), Map.of()));
        when(specFidelityCritic.critique(any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());
        SpecFidelityCriticService renderingDelegate = new SpecFidelityCriticService(null, new ObjectMapper());
        when(specFidelityCritic.renderForRetryPrompt(any())).thenAnswer(invocation -> renderingDelegate.renderForRetryPrompt(invocation.getArgument(0)));

        testCaseRepository = mock(ProgrammingExerciseTestCaseTestRepository.class);
        when(testCaseRepository.findByExerciseId(anyLong())).thenReturn(Set.of());
        service = newService();

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(null);
        when(exercise.getProgrammingLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        user = mock(User.class);
        when(user.getId()).thenReturn(7L);
    }

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
        return service.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, cancelled, null, null, null);
    }

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

    @Test
    void adaptMode_passesThePersistedGradedTestNamesAsTheTotalWipeBaseline() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(testCaseRepository.findByExerciseId(42L)).thenReturn(Set.of(testCase("evictsLeastRecentlyUsed"), testCase("capacityIsRespected")));

        try (GenerationOutcome outcome = service.generate(exercise, user, "Tighten the eviction test.", JOB_ID, GenerationMode.ADAPT, () -> false, null, null, null)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        ArgumentCaptor<VerificationRequest> requestCaptor = ArgumentCaptor.forClass(VerificationRequest.class);
        verify(verifier).verify(any(), anyString(), any(), requestCaptor.capture());
        verify(workspace).seedWorkspace(sandbox, SESSION_ID, exercise, GenerationMode.ADAPT);
        assertThat(requestCaptor.getValue().baselineGradedTestNames()).as("ADAPT hands the persisted graded test names to the total-wipe gate")
                .containsExactlyInAnyOrder("evictsLeastRecentlyUsed", "capacityIsRespected");
    }

    @Test
    void generateMode_passesAnEmptyTotalWipeBaselineAndDoesNotQueryPersistedTests() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        verify(workspace).seedWorkspace(sandbox, SESSION_ID, exercise, GenerationMode.GENERATE);

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

    @Test
    void cancelledLoopResult_skipsVerificationAndDestroysSession() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, ""));

        GenerationOutcome outcome = generate(() -> false);

        assertThat(outcome.isAccepted()).isFalse();
        verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class));
        verify(sandbox).destroySession(SESSION_ID);
    }

    @Test
    void cancellationBetweenTurns_skipsVerificationAndDestroysSession() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        BooleanSupplier cancelled = () -> true;

        GenerationOutcome outcome = generate(cancelled);

        assertThat(outcome.isAccepted()).isFalse();
        verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class));
        verify(sandbox).destroySession(SESSION_ID);
    }

    @Test
    void thrownExceptionFromLoop_destroysSessionAndPropagates() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenThrow(new RuntimeException("model exploded"));

        assertThatThrownBy(() -> generate(() -> false)).isInstanceOf(RuntimeException.class).hasMessageContaining("model exploded");

        verify(sandbox, atLeastOnce()).destroySession(SESSION_ID);
        verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class));
    }

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

    private static SpecFidelityReport reportWith(String requirement) {
        return new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, requirement, "no test covers it")));
    }

    @ParameterizedTest
    @EnumSource(SpecFidelityReport.Kind.class)
    void acceptedVerification_isBlockedOnlyByBlockingFindings(SpecFidelityReport.Kind kind) {
        Set<SpecFidelityReport.Kind> expectedBlockingKinds = EnumSet.of(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE,
                SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING, SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE);
        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(kind, "requirement", "detail")));
        GenerationOutcome outcome = new GenerationOutcome(completed(), accepted(), null, null, null, Map.of(), "", report, Map.of());

        boolean expectedBlocking = expectedBlockingKinds.contains(kind);
        assertThat(report.hasBlockingFindings()).isEqualTo(expectedBlocking);
        assertThat(outcome.isAccepted()).isEqualTo(!expectedBlocking);
    }

    @Test
    void acceptedWithAdvisoryFindings_doesNotSpendRetryOrFlipVerdict() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any())).thenReturn(reportWith("CJK characters"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).as("an oracle-accepted exercise stays accepted with advisory findings").isTrue();
            assertThat(outcome.specFidelityReport().hasFindings()).isTrue();
        }
        verify(agentLoopRunner).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void acceptedAdaptationWithOnlyAdvisoryFindings_staysAccepted() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any())).thenReturn(reportWith("add assertion messages"));

        try (GenerationOutcome outcome = service.generate(exercise, user, "Change remove only and preserve everything else", "job", GenerationMode.ADAPT, () -> false, null, null,
                response -> {
                })) {
            assertThat(outcome.isAccepted()).isTrue();
            assertThat(outcome.specFidelityReport().hasFindings()).isTrue();
        }
        verify(agentLoopRunner).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void acceptedAdaptationWithPersistentScopeDrift_isNotAcceptedForLivePersistence() {
        SpecFidelityReport scopeDrift = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE,
                "solution/src/Inventory.java removed displayName(String)", "The feedback explicitly required preserving it.")));
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any())).thenReturn(scopeDrift);

        try (GenerationOutcome outcome = service.generate(exercise, user, "Change only remove; preserve displayName", "job", GenerationMode.ADAPT, () -> false, null, null,
                response -> {
                })) {
            assertThat(outcome.isAccepted()).as("unresolved adaptation scope drift must go to the isolated review draft, never live persistence").isFalse();
            assertThat(outcome.specFidelityReport().hasBlockingFindings()).isTrue();
        }
        verify(agentLoopRunner, times(MAX_GENERATION_ATTEMPTS)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void acceptedAdaptationWithCorrectableScopeFinding_retriesAndAcceptsTheRepair() {
        String feedback = "Reject zero quantities and preserve this full instructor context: " + "context ".repeat(40);
        SpecFidelityReport missingChange = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING,
                "reject zero quantities", "The candidate does not add the requested validation.")));
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(), completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted(), accepted());
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any())).thenReturn(missingChange, SpecFidelityReport.empty());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome outcome = service.generate(exercise, user, feedback, "job", GenerationMode.ADAPT, () -> false, null, null, response -> {
        })) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        verify(agentLoopRunner, times(2)).run(anyString(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        assertThat(promptCaptor.getAllValues().get(1)).contains("Requested adaptation change missing or incomplete", "reject zero quantities", feedback);
    }

    @Test
    void unchangedAdaptationIsRejectedAsMissingTheRequestedChange() {
        when(exercise.getProblemStatement()).thenReturn("PROBLEM STATEMENT");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any())).thenReturn(new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING, "Change one method only", "The candidate is unchanged."))));

        try (GenerationOutcome outcome = service.generate(exercise, user, "Change one method only", JOB_ID, GenerationMode.ADAPT, () -> false, null, null, null)) {
            assertThat(outcome.isAccepted()).isFalse();
            assertThat(outcome.specFidelityReport().findings()).singleElement()
                    .satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING));
        }

        verify(specFidelityCritic, times(MAX_GENERATION_ATTEMPTS)).critiqueAdaptation(eq("Change one method only"), eq("PROBLEM STATEMENT"), any(), eq(""), any());
        verify(agentLoopRunner, times(MAX_GENERATION_ATTEMPTS)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(specFidelityCritic, never()).critique(any(), any(), any(), any());
    }

    @Test
    void adaptationCriticExceptionOrTruncatedEvidence_neverAllowsLivePersistence() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(workspace.seedWorkspace(any(), anyString(), any(), any())).thenReturn(
                new GenerationWorkspaceService.WorkspaceSeed(Map.of(), Map.of(), Map.of(), Map.of(RepositoryType.SOLUTION, Map.of("src/Huge.java", "x".repeat(30_000)))));
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("critic plumbing failed"));

        try (GenerationOutcome outcome = service.generate(exercise, user, "Change one method only", "job", GenerationMode.ADAPT, () -> false, null, null, response -> {
        })) {
            assertThat(outcome.isAccepted()).isFalse();
            assertThat(outcome.specFidelityReport().hasBlockingFindings()).isTrue();
        }
    }

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
        assertThat(retryPrompt).as("and also the advisory spec-fidelity gap").contains("optional quality improvements").contains("emoji").contains("Add a test");
    }

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

    @Test
    void extractTaskBoundTestNames_dedupesAndTrims() {
        assertThat(GenerationOrchestrationService.extractTaskBoundTestNames("")).isEmpty();
        assertThat(GenerationOrchestrationService.extractTaskBoundTestNames("[task][A]( t1 , t2 )\n[task][B](t2,t3)")).containsExactly("t1", "t2", "t3");
    }

    @Test
    void adaptationChangeSummary_preservesOrderingEvidenceAndReportsTruncation() {
        String reordered = GenerationOrchestrationService.renderAdaptationChanges("same", "same",
                Map.of(RepositoryType.SOLUTION, Map.of("src/Example.java", "first\nsecond\nthird\n")),
                Map.of(RepositoryType.SOLUTION, Map.of("src/Example.java", "second\nfirst\nthird\n")));

        assertThat(reordered).contains("--- solution/src/Example.java").contains("- second").contains("+ second");

        String truncated = GenerationOrchestrationService.renderAdaptationChanges("", "x".repeat(30_000), Map.of(), Map.of());
        assertThat(truncated).contains("change summary truncated").hasSizeLessThanOrEqualTo(24_000);
    }

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
        assertThat(prompts.get(0)).as("attempt 1 carries the seeded layout").startsWith("=== INITIAL WORKSPACE").endsWith("Build a bubble sort exercise.");
        assertThat(prompts.get(1)).as("the retry is rebuilt from the rejection report and does NOT replay the stale turn-0 layout").doesNotContain("INITIAL WORKSPACE")
                .contains("template unexpectedly passed all tests");
    }

    @Test
    void prependWorkspaceLayout_delimitsLayoutAndPreservesBrief() {
        assertThat(GenerationOrchestrationService.prependWorkspaceLayout("", "BRIEF")).isEqualTo("BRIEF");
        assertThat(GenerationOrchestrationService.prependWorkspaceLayout("   ", "BRIEF")).isEqualTo("BRIEF");
        assertThat(GenerationOrchestrationService.prependWorkspaceLayout(null, "BRIEF")).isEqualTo("BRIEF");

        String prepended = GenerationOrchestrationService.prependWorkspaceLayout("LAYOUT", "BRIEF");
        assertThat(prepended).isEqualTo("=== INITIAL WORKSPACE (seeded; you do not need to re-list it) ===\nLAYOUT\n=== END INITIAL WORKSPACE ===\n\nBRIEF");
    }

    @Test
    void nullAndEmptyProblemStatements_doNotCreateAHeaderOnlyAdaptationChange() {
        String changes = GenerationOrchestrationService.renderAdaptationChanges(null, "", Map.of(), Map.of());

        assertThat(changes).isEmpty();
    }

    @Test
    void authoritativeVerify_runsInTheGenerationSession() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
        }

        verify(verifier).verify(eq(sandbox), eq(SESSION_ID), eq(exercise), any(VerificationRequest.class));
        verify(workspace).materializeRepositoryFiles(eq(sandbox), eq(SESSION_ID), any(), any());
        verify(sandbox, times(1)).createSession(any());
    }

    @Test
    void cancellationDuringAuthoritativeVerify_returnsCancelledAndSkipsCritic() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenAnswer(invocation -> {
            cancelled.set(true);
            return accepted();
        });

        GenerationOutcome outcome = generate(cancelled::get);

        assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        assertThat(outcome.isAccepted()).isFalse();
        verify(specFidelityCritic, never()).critique(any(), any(), any(), any());
        verify(sandbox, atLeastOnce()).destroySession(SESSION_ID);
    }

    @Test
    void budgetExhaustedLoop_stillRunsAuthoritativeVerificationInTheGenerationSession() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any()))
                .thenReturn(new AgentLoopResult(AgentLoopResult.Status.BUDGET_EXHAUSTED, 100, "ran out of turns"));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(rejected("template passed a graded test"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
            assertThat(outcome.isAccepted()).as("a rejected budget-exhausted run is not accepted").isFalse();
        }

        verify(verifier, atLeastOnce()).verify(eq(sandbox), eq(SESSION_ID), any(), any(VerificationRequest.class));
    }

    @Test
    void acceptedOutcome_reusesVerificationExtractions_soPersistDoesNotReReadTheSandbox() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class))).thenReturn(accepted());
        when(workspace.extractRepository(sandbox, SESSION_ID, RepositoryType.TESTS, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("tests/T.java", "t ${testWorkingDirectory}"), false));
        when(workspace.extractRepository(sandbox, SESSION_ID, RepositoryType.TEMPLATE, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("template/M.java", "m"), false));
        when(workspace.extractRepository(sandbox, SESSION_ID, RepositoryType.SOLUTION, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("solution/S.java", "s"), false));
        when(workspace.extractProblemStatement(sandbox, SESSION_ID)).thenReturn("  # Title\n\nStatement  \n");

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isAccepted()).isTrue();
            assertThat(outcome.producedFiles(RepositoryType.TESTS)).containsExactlyEntriesOf(Map.of("tests/T.java", "t tests"));
            assertThat(outcome.producedFiles(RepositoryType.TEMPLATE)).containsExactlyEntriesOf(Map.of("template/M.java", "m"));
            assertThat(outcome.producedFiles(RepositoryType.SOLUTION)).containsExactlyEntriesOf(Map.of("solution/S.java", "s"));
            assertThat(outcome.producedProblemStatement()).isEqualTo("# Title\n\nStatement");
        }

        verify(workspace, times(1)).extractRepository(sandbox, SESSION_ID, RepositoryType.TESTS, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY);
        verify(workspace, times(1)).extractRepository(sandbox, SESSION_ID, RepositoryType.TEMPLATE, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY);
        verify(workspace, times(1)).extractRepository(sandbox, SESSION_ID, RepositoryType.SOLUTION, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY);
        verify(workspace, never()).extractRepositoryFiles(any(), anyString(), any());
        ArgumentCaptor<VerificationRequest> request = ArgumentCaptor.forClass(VerificationRequest.class);
        verify(verifier).verify(eq(sandbox), eq(SESSION_ID), eq(exercise), request.capture());
        assertThat(request.getValue().producedProblemStatement()).isEqualTo("# Title\n\nStatement");
    }
}
