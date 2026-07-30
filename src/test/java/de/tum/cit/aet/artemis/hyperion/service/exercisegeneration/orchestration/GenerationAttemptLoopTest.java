package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.FakeInteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SemanticMutantOutcome.Disposition;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StructuralOracleSeedingService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationRequest;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The attempt loop's own decisions, driven through {@link GenerationAttemptLoop#run()} rather than through
 * {@link GenerationOrchestrationService#generate}.
 * <p>
 * Everything the owning service's public seam can already observe stays covered there, in {@code GenerationOrchestrationServiceTest}: the termination reason of every ordinary
 * exit, the per-round finding-drain counts, the mechanical/semantic interleaving, and the ADAPT budget clamp (which
 * {@code acceptedAdaptationWithPersistentScopeDriftRemainsMechanicallyAcceptedAndRequiresReview} kills by pinning the attempt count at two). Duplicating those here would buy a
 * second way to fail for the same defect and nothing else.
 * <p>
 * What is covered here is what that seam cannot reach:
 * <ul>
 * <li>the repair <b>scope</b> handed to the agent tools — the service builds {@code SandboxAgentTools} internally, so no caller of {@code generate} can see which write barrier a
 * repair round runs under, and today nothing asserts that the scheduled surface's writable roots are the ones the agent actually gets;</li>
 * <li>the loop's own <b>fairness bookkeeping</b> across rounds ({@code markSurfaceRepaired}), which decides what {@link SemanticRepairBatch#next} is asked — the batch's reply to
 * a given state is a pure function covered by {@code SemanticRepairBatchTest}, but nothing checked that the loop feeds it the right state;</li>
 * <li>budgets and caps at a <b>configured boundary</b>: the service derives the attempt cap from the repair budget, so a degenerate or exact-boundary configuration is only
 * reachable by constructing the loop directly.</li>
 * </ul>
 */
class GenerationAttemptLoopTest {

    private static final String SESSION_ID = FakeInteractiveSandbox.SESSION_ID;

    private static final String SPEC_PATH = GenerationWorkspaceService.WORKSPACE + "/SPEC.md";

    private GenerationOrchestrationService service;

    private GenerationWorkspaceService workspace;

    private AgentLoopRunner agentLoopRunner;

    private DifferentialVerificationService verifier;

    private StructuralOracleSeedingService structuralOracleSeeder;

    private SpecFidelityCriticService specFidelityCritic;

    private GenerationJobService jobService;

    private StagedGenerationRunner stagedGenerationRunner;

    private SandboxAgentTools baseTools;

    private FakeInteractiveSandbox sandbox;

    private ProgrammingExercise exercise;

    /** Every instructor-facing progress line the run emitted, in order. */
    private final List<String> progressLines = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = mock(GenerationOrchestrationService.class);
        workspace = mock(GenerationWorkspaceService.class);
        agentLoopRunner = mock(AgentLoopRunner.class);
        verifier = mock(DifferentialVerificationService.class);
        structuralOracleSeeder = mock(StructuralOracleSeedingService.class);
        specFidelityCritic = mock(SpecFidelityCriticService.class);
        jobService = mock(GenerationJobService.class);
        stagedGenerationRunner = mock(StagedGenerationRunner.class);
        // A mock, not the shared fake: the repair scope is a write barrier inside the tools object with no reader, so the call carrying the scheduled surface's roots is the only
        // observable form the decision takes.
        baseTools = mock(SandboxAgentTools.class);
        sandbox = new FakeInteractiveSandbox();
        progressLines.clear();

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);
        when(exercise.getProgrammingLanguage()).thenReturn(ProgrammingLanguage.JAVA);

        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(structuralOracleSeeder.seedIfStructuralDiff(any(), anyString(), any())).thenReturn(Set.of());
        when(workspace.extractRepository(any(), anyString(), any(), any())).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of(), false));
        java.util.concurrent.atomic.AtomicInteger candidate = new java.util.concurrent.atomic.AtomicInteger();
        when(workspace.extractProblemStatement(any(), anyString())).thenAnswer(ignored -> "PROBLEM STATEMENT " + candidate.incrementAndGet());
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());
        when(specFidelityCritic.detectMessagelessAssertions(any(), any())).thenReturn(List.of());
        when(specFidelityCritic.detectUnenforceableTechniqueRules(any())).thenReturn(List.of());
        SpecFidelityCriticService renderingDelegate = new SpecFidelityCriticService(null, new ObjectMapper());
        when(specFidelityCritic.renderForRetryPrompt(any())).thenAnswer(invocation -> renderingDelegate.renderForRetryPrompt(invocation.getArgument(0)));
    }

    /**
     * A loop over the fixture above.
     *
     * @param mode                  the run mode, which also decides the semantic repair budget's ceiling
     * @param maxGenerationAttempts how many authoring attempts the loop may make
     * @param maxSemanticRepairs    the semantic repair budget a GENERATE run gets
     */
    private GenerationAttemptLoop newLoop(GenerationMode mode, int maxGenerationAttempts, int maxSemanticRepairs) {
        return newLoop(mode, maxGenerationAttempts, maxSemanticRepairs, false);
    }

    private GenerationAttemptLoop newLoop(GenerationMode mode, int maxGenerationAttempts, int maxSemanticRepairs, boolean stagedGenerationEnabled) {
        return newLoop(mode, maxGenerationAttempts, maxSemanticRepairs, stagedGenerationEnabled, () -> false);
    }

    private GenerationAttemptLoop newLoop(GenerationMode mode, int maxGenerationAttempts, int maxSemanticRepairs, boolean stagedGenerationEnabled, BooleanSupplier cancelled) {
        GenerationAttemptLoop.Dependencies dependencies = new GenerationAttemptLoop.Dependencies(workspace, agentLoopRunner, verifier, structuralOracleSeeder, specFidelityCritic,
                jobService, stagedGenerationRunner, new AgentTranscriptWriter(""), stagedGenerationEnabled, 100, maxGenerationAttempts, maxSemanticRepairs);
        GenerationAttemptLoop.RunContext context = new GenerationAttemptLoop.RunContext(exercise, mode, "job-1", sandbox, SESSION_ID,
                new GenerationWorkspaceService.WorkspaceSeed(Map.of(), Map.of()), Map.of(), Map.of(), Map.of(), null, Set.of(), "Build a bubble sort exercise.", true, true,
                "SYSTEM_PROMPT", "FIRST_PROMPT", baseTools, baseTools, cancelled, progressLines::add, response -> {
                });
        return new GenerationAttemptLoop(service, dependencies, context);
    }

    private GenerationAttemptLoop newGenerateLoop(int maxGenerationAttempts, int maxSemanticRepairs) {
        return newLoop(GenerationMode.GENERATE, maxGenerationAttempts, maxSemanticRepairs);
    }

    private static AgentLoopRunner.AgentLoopSession loopSession(AgentLoopResult result) {
        return new AgentLoopRunner.AgentLoopSession(result, List.of());
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

    @Test
    void unresolvedPreFreezeSpecificationFindingsCannotDisappearFromTheFinalReview() {
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new StagedGenerationRunner.StagedRunOutcome(completed(), null, List.of("R1 mandates an unrequested implementation technique")));

        GenerationAttemptLoop loop = newLoop(GenerationMode.GENERATE, 2, 1, true);
        loop.run();

        assertThat(loop.specFidelityReport().findings()).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.SPECIFICATION_REVIEW_FINDING);
            assertThat(finding.requirement()).contains("unrequested implementation technique");
        });
        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.NO_SCHEDULABLE_SURFACE);
    }

    @Test
    void aCleanTextOnlyRetryCannotEraseAnInconclusivePreFreezeSpecificationReview() {
        String unresolved = "The automated specification quality review was inconclusive, so the contract requires instructor review.";
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new StagedGenerationRunner.StagedRunOutcome(completed(), null, List.of(unresolved)));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(SpecFidelityReport.qualityReviewUnavailable("The full-artifact review did not complete."), SpecFidelityReport.empty());

        GenerationAttemptLoop loop = newLoop(GenerationMode.GENERATE, 2, 1, true);
        loop.run();

        verify(specFidelityCritic, times(2)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(loop.specFidelityReport().findings())
                .anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.SPECIFICATION_REVIEW_FINDING && finding.requirement().equals(unresolved) && finding.isBlocking());
        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.NO_SCHEDULABLE_SURFACE);
    }

    @Test
    void anUnapprovedSpecificationCannotAuthorizeValidatedWitnessAdoption() {
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new StagedGenerationRunner.StagedRunOutcome(completed(), null, List.of("The specification review was inconclusive.")));
        sandbox.withFile(SPEC_PATH, "## Rules\nR1. The result is selected cyclically.");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/CycleTest.java", "class CycleTest {}"), false));
        ContractWitness witness = new ContractWitness("R1", "wrapsToFirst", "void wrapsToFirst() {}", "stops after the final element");
        SemanticMutant mutant = new SemanticMutant("R1", "src/Cycle.java", "class Cycle {}", "class Cycle { int stopsEarly; }", witness);
        when(specFidelityCritic.authorSemanticMutants(anyString(), any(), any(), any(), any())).thenReturn(List.of(mutant));
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE)));
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(witness));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(List.of(new ContractWitnessOutcome(witness, ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED, "")));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(SpecFidelityReport.qualityReviewUnavailable("The first full-artifact review did not complete."), SpecFidelityReport.empty());

        GenerationAttemptLoop loop = newLoop(GenerationMode.GENERATE, 3, 2, true);
        loop.run();

        verify(specFidelityCritic, times(2)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(baseTools, never()).enterRepairScope(any());
        assertThat(loop.specFidelityReport().findings()).anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL
                && finding.detail().contains(witness.testName()) && !finding.isBlocking());
        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.NO_SCHEDULABLE_SURFACE);
    }

    @Test
    void aFailedRecheckCannotEraseAProvenSemanticSurvivorOrConverge() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | globally cheapest request |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/SchedulerTest.java", "package p; class SchedulerTest {}"), false));
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Scheduler.java", "class Scheduler {}"), false));
        ContractWitness counterexample = new ContractWitness("R1", "hyperionMutantGlobalChoice", "@Test void hyperionMutantGlobalChoice() { assertEquals(1, choose()); }",
                "chooses within the first batch");
        SemanticMutant mutant = new SemanticMutant("R1", "src/Scheduler.java", "class Scheduler {}", "class Scheduler {}", counterexample);
        when(specFidelityCritic.authorSemanticMutants(anyString(), any(), any(), any(), any())).thenReturn(List.of(mutant));
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE)));
        DifferentialVerificationService.VerificationInfrastructureException failure = new DifferentialVerificationService.VerificationInfrastructureException("recheck failed",
                new IllegalStateException("reports unavailable"));
        when(verifier.checkSemanticMutants(any(), anyString(), any(), any(), any(), any())).thenThrow(failure);
        GenerationAttemptLoop loop = newGenerateLoop(2, 1);

        assertThatThrownBy(loop::run).isSameAs(failure);
        assertThat(loop.lastMechanicallyVerifiedCandidate()).isNotNull();
        assertThat(loop.lastMechanicallyVerifiedCandidate().reviewReport().findings()).anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE);
    }

    @Test
    void aContractRepairRechecksProvenMutantEvidenceDuringTheNextReviewBeforeSchedulingOracleWork() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | globally cheapest request |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/SchedulerTest.java", "package p; class SchedulerTest {}"), false));
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Scheduler.java", "class Scheduler {}"), false));
        SpecFidelityReport contractReview = report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION);
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(contractReview, SpecFidelityReport.empty());
        ContractWitness counterexample = new ContractWitness("R1", "hyperionMutantGlobalChoice", "@Test void hyperionMutantGlobalChoice() { assertEquals(1, choose()); }",
                "chooses within the first batch");
        SemanticMutant mutant = new SemanticMutant("R1", "src/Scheduler.java", "class Scheduler {}", "class Scheduler { int localOnly; }", counterexample);
        when(specFidelityCritic.authorSemanticMutants(anyString(), any(), any(), any(), any())).thenReturn(List.of(mutant), List.of());
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE)), List.of());
        when(verifier.checkSemanticMutants(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE)));

        GenerationAttemptLoop loop = newGenerateLoop(2, 1);
        loop.run();

        verify(baseTools).enterRepairScope(Set.of("solution", "template", "tests", "test-plan.json", "problem-statement.md"));
        verify(verifier).checkSemanticMutants(any(), anyString(), any(), any(), any(), any());
        assertThat(loop.specFidelityReport().findings()).anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE);
        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.ATTEMPT_CAP_REACHED);
    }

    @Test
    void anInconclusivePendingMutantRecheckUnderAnUnapprovedSpecificationNeverBecomesCurrentSurvivorEvidence() {
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new StagedGenerationRunner.StagedRunOutcome(completed(), null, List.of("The specification review was inconclusive.")));
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | globally cheapest request |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/SchedulerTest.java", "package p; class SchedulerTest {}"), false));
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Scheduler.java", "class Scheduler {}"), false));
        SpecFidelityReport advisoryCollision = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE,
                "Reviewer mentions globalChoice", "Advisory prose repeats R1, src/Scheduler.java, and globalChoice but is not executable evidence.")));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION), advisoryCollision);
        ContractWitness counterexample = new ContractWitness("R1", "hyperionMutantGlobalChoice", "@Test void hyperionMutantGlobalChoice() { assertEquals(1, choose()); }",
                "chooses within the first batch");
        SemanticMutant mutant = new SemanticMutant("R1", "src/Scheduler.java", "class Scheduler {}", "class Scheduler { int localOnly; }", counterexample);
        when(specFidelityCritic.authorSemanticMutants(anyString(), any(), any(), any(), any())).thenReturn(List.of(mutant), List.of());
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE)), List.of());
        when(verifier.checkSemanticMutants(any(), anyString(), any(), any(), any(), any())).thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE)));

        GenerationAttemptLoop loop = newGenerateLoop(3, 2);
        loop.run();

        assertThat(loop.specFidelityReport().findings()).anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE && finding.detail().contains("R1")
                && finding.detail().contains("src/Scheduler.java") && finding.detail().contains("hyperionMutantGlobalChoice") && finding.detail().contains("remains unresolved"));
        assertThat(loop.specFidelityReport().findings()).noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE
                || finding.kind() == SpecFidelityReport.Kind.EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL);
        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
    }

    @Test
    void aLaterProbeFailureCannotEraseARevalidatedSemanticMutant() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | globally cheapest request |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/SchedulerTest.java", "package p; class SchedulerTest {}"), false));
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Scheduler.java", "class Scheduler {}"), false));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION), SpecFidelityReport.empty());
        SemanticMutant mutant = mutant("R1", "globalChoice", "chooses within the first batch");
        when(specFidelityCritic.authorSemanticMutants(anyString(), any(), any(), any(), any())).thenReturn(List.of(mutant), List.of());
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE)));
        when(verifier.checkSemanticMutants(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE)));
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of())
                .thenThrow(new IllegalStateException("witness reviewer unavailable"));

        GenerationAttemptLoop loop = newGenerateLoop(3, 2);
        loop.run();

        assertThat(loop.specFidelityReport().findings()).anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE && finding.detail().contains("R1")
                && finding.detail().contains("src/Scheduler.java") && finding.detail().contains("globalChoice"));
        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
    }

    @Test
    void onlyRenderedSurvivorsBecomeOracleAcceptanceChecksWhileInconclusiveEvidenceIsRechecked() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | honor every prerequisite |\n| R2 | break ties lexically |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/SchedulerTest.java", "package p; class SchedulerTest {}"), false));
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Scheduler.java", "class Scheduler {}"), false));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION), SpecFidelityReport.empty(), SpecFidelityReport.empty());
        SemanticMutant firstOnly = mutant("R1", "allPrerequisites", "honors only the first prerequisite");
        SemanticMutant fifo = mutant("R2", "lexicalTie", "uses FIFO insertion order");
        when(specFidelityCritic.authorSemanticMutants(anyString(), any(), any(), any(), any())).thenReturn(List.of(firstOnly, fifo), List.of(), List.of());
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), any(), any(), any(), any())).thenReturn(
                List.of(new SemanticMutantOutcome(firstOnly, Disposition.SURVIVED_GRADED_SUITE), new SemanticMutantOutcome(fifo, Disposition.SURVIVED_GRADED_SUITE)), List.of(),
                List.of());
        when(verifier.checkSemanticMutants(any(), anyString(), any(), any(), any(), any())).thenReturn(
                List.of(new SemanticMutantOutcome(firstOnly, Disposition.SURVIVED_GRADED_SUITE), new SemanticMutantOutcome(fifo, Disposition.INCONCLUSIVE)),
                List.of(new SemanticMutantOutcome(firstOnly, Disposition.KILLED_BY_GRADED_SUITE)), List.of(new SemanticMutantOutcome(fifo, Disposition.KILLED_BY_GRADED_SUITE)));

        GenerationAttemptLoop loop = newGenerateLoop(3, 2);
        loop.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SemanticMutant>> checkedMutants = ArgumentCaptor.forClass(List.class);
        verify(verifier, times(3)).checkSemanticMutants(any(), anyString(), any(), any(), checkedMutants.capture(), any());
        assertThat(checkedMutants.getAllValues()).containsExactly(List.of(firstOnly, fifo), List.of(firstOnly), List.of(fifo));
        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.CONVERGED);
    }

    @Test
    void priorInconclusiveEvidenceOwnsTheBoundedStateBeforeFreshSampling() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | one |\n| R2 | two |\n| R3 | three |\n| R4 | four |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/SchedulerTest.java", "package p; class SchedulerTest {}"), false));
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Scheduler.java", "class Scheduler {}"), false));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION), SpecFidelityReport.empty());
        List<SemanticMutant> mutants = List.of(mutant("R1", "one", "wrong one"), mutant("R2", "two", "wrong two"), mutant("R3", "three", "wrong three"),
                mutant("R4", "four", "wrong four"));
        when(specFidelityCritic.authorSemanticMutants(anyString(), any(), any(), any(), any())).thenReturn(mutants);
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(mutants.stream().map(mutant -> new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE)).toList());
        when(verifier.checkSemanticMutants(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(mutants.stream().map(mutant -> new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE)).toList());

        GenerationAttemptLoop loop = newGenerateLoop(3, 2);
        loop.run();

        verify(specFidelityCritic, times(1)).authorSemanticMutants(anyString(), any(), any(), any(), any());
        assertThat(loop.specFidelityReport().findings()).filteredOn(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE).hasSize(4);
        assertThat(loop.specFidelityReport().findings()).extracting(SpecFidelityReport.Finding::detail).anySatisfy(evidence -> assertThat(evidence).contains("R1"))
                .anySatisfy(evidence -> assertThat(evidence).contains("R2")).anySatisfy(evidence -> assertThat(evidence).contains("R3"))
                .anySatisfy(evidence -> assertThat(evidence).contains("R4"));
        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
    }

    @Test
    void contractThenOracleRepairKillsTheRevalidatedMutantAndConverges() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | honor every prerequisite |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/SchedulerTest.java", "package p; class SchedulerTest {}"), false));
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Scheduler.java", "class Scheduler {}"), false));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION), SpecFidelityReport.empty(), SpecFidelityReport.empty());
        ContractWitness counterexample = new ContractWitness("R1", "allPrerequisites", "@Test void allPrerequisites() { assertEquals(3, schedule().size()); }",
                "honors only the first prerequisite");
        SemanticMutant mutant = new SemanticMutant("R1", "src/Scheduler.java", "class Scheduler {}", "class Scheduler { int firstOnly; }", counterexample);
        when(specFidelityCritic.authorSemanticMutants(anyString(), any(), any(), any(), any())).thenReturn(List.of(mutant), List.of(), List.of());
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE)), List.of(), List.of());
        when(verifier.checkSemanticMutants(any(), anyString(), any(), any(), any(), any())).thenReturn(
                List.of(new SemanticMutantOutcome(mutant, Disposition.SURVIVED_GRADED_SUITE)), List.of(new SemanticMutantOutcome(mutant, Disposition.KILLED_BY_GRADED_SUITE)));

        GenerationAttemptLoop loop = newGenerateLoop(3, 2);
        loop.run();

        InOrder scopes = Mockito.inOrder(baseTools);
        scopes.verify(baseTools).enterRepairScope(Set.of("solution", "template", "tests", "test-plan.json", "problem-statement.md"));
        scopes.verify(baseTools).enterRepairScope(Set.of("tests", "test-plan.json", "problem-statement.md"));
        verify(verifier, times(2)).checkSemanticMutants(any(), anyString(), any(), any(), any(), any());
        assertThat(loop.specFidelityReport().findings()).noneMatch(SpecFidelityReport.Finding::isBlocking);
        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.CONVERGED);
    }

    @Test
    void adjudicatedReferenceFailureBlocksConvergenceUntilTheExactWitnessPasses() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | every admitted boundary returns the mathematically nearest value |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/NearestTest.java", "class NearestTest {}"), false));
        ContractWitness witness = new ContractWitness("R1", "extremeBoundary", "@Test void extremeBoundary() { assertEquals(0, choose()); }",
                "uses overflowing integer subtraction");
        SpecFidelityReport.Finding defect = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                "Reference solution violates R1 in executable witness extremeBoundary", "the named reference assertion failed and independent review grounded it");
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(witness));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), any(), any(), any())).thenReturn(
                List.of(new ContractWitnessOutcome(witness, ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, "expected zero")),
                List.of(new ContractWitnessOutcome(witness, ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED, "")));
        when(specFidelityCritic.adjudicateReferenceWitnesses(anyString(), anyString(), any(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.ReferenceWitnessReview(List.of(defect), List.of(witness)), SpecFidelityCriticService.ReferenceWitnessReview.empty());

        GenerationAttemptLoop loop = newGenerateLoop(2, 1);
        loop.run();

        verify(baseTools).enterRepairScope(Set.of("solution", "template", "tests", "test-plan.json", "problem-statement.md"));
        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.CONVERGED);
        assertThat(loop.specFidelityReport().findings()).noneMatch(SpecFidelityReport.Finding::isBlocking);
    }

    @Test
    void inconclusiveRecheckCannotEraseAnAdjudicatedReferenceFailure() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | every admitted boundary returns the mathematically nearest value |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/NearestTest.java", "class NearestTest {}"), false));
        ContractWitness witness = new ContractWitness("R1", "extremeBoundary", "@Test void extremeBoundary() { assertEquals(0, choose()); }",
                "uses overflowing integer subtraction");
        SpecFidelityReport.Finding defect = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION,
                "Reference solution violates R1 in executable witness extremeBoundary", "the named reference assertion failed and independent review grounded it");
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(witness));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), any(), any(), any())).thenReturn(
                List.of(new ContractWitnessOutcome(witness, ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, "expected zero")),
                List.of(new ContractWitnessOutcome(witness, ContractWitnessOutcome.Disposition.INCONCLUSIVE, "test report unavailable")));
        when(specFidelityCritic.adjudicateReferenceWitnesses(anyString(), anyString(), any(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.ReferenceWitnessReview(List.of(defect), List.of(witness)), SpecFidelityCriticService.ReferenceWitnessReview.empty());

        GenerationAttemptLoop loop = newGenerateLoop(2, 1);
        loop.run();

        assertThat(loop.terminationReason()).isNotEqualTo(TerminationReason.CONVERGED);
        assertThat(loop.specFidelityReport().findings()).anyMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    @Test
    void cleanTextReviewRetryCannotEraseAReferenceFailureAwaitingAdjudication() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | every admitted boundary returns the mathematically nearest value |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/NearestTest.java", "class NearestTest {}"), false));
        ContractWitness witness = new ContractWitness("R1", "extremeBoundary", "@Test void extremeBoundary() { assertEquals(0, choose()); }",
                "uses overflowing integer subtraction");
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(witness));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(List.of(new ContractWitnessOutcome(witness, ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, "expected zero")));
        when(specFidelityCritic.adjudicateReferenceWitnesses(anyString(), anyString(), any(), any(), any())).thenReturn(SpecFidelityCriticService.ReferenceWitnessReview.empty());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());

        GenerationAttemptLoop loop = newGenerateLoop(2, 1);
        loop.run();

        assertThat(loop.terminationReason()).isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
        assertThat(loop.specFidelityReport().findings()).anyMatch(
                finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE && finding.detail().contains("Executable reference evidence remains unresolved"));
        verify(specFidelityCritic, times(2)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aProbeRestoreFailureEscapesReviewWithThePreReviewCheckpointIntact() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | globally cheapest request |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/SchedulerTest.java", "package p; class SchedulerTest {}"), false));
        ContractWitness counterexample = new ContractWitness("R1", "hyperionMutantGlobalChoice", "@Test void hyperionMutantGlobalChoice() { assertEquals(1, choose()); }",
                "chooses within the first batch");
        SemanticMutant mutant = new SemanticMutant("R1", "src/Scheduler.java", "class Scheduler {}", "class Scheduler { int changed; }", counterexample);
        when(specFidelityCritic.authorSemanticMutants(anyString(), any(), any(), any(), any())).thenReturn(List.of(mutant));
        DifferentialVerificationService.VerificationInfrastructureException failure = new DifferentialVerificationService.VerificationInfrastructureException("restore failed",
                new IllegalStateException("session lost"));
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), any(), any(), any(), any())).thenThrow(failure);
        GenerationAttemptLoop loop = newGenerateLoop(2, 1);

        assertThatThrownBy(loop::run).isSameAs(failure);
        assertThat(loop.lastMechanicallyVerifiedCandidate()).isNotNull();
        assertThat(loop.lastMechanicallyVerifiedCandidate().verification().mechanicallyVerified()).isTrue();
    }

    @Test
    void cancellationAfterRepairReviewPreservesTheNewlyReviewedCandidate() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger testsExtraction = new AtomicInteger();
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any())).thenAnswer(invocation -> {
            String path = testsExtraction.getAndIncrement() == 0 ? "test/OriginalTest.java" : "test/ImprovedTest.java";
            return new GenerationWorkspaceService.RepositoryExtraction(Map.of(path, "class Test {}"), false);
        });
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(new VerificationResult(true, true, true, 7, List.of()),
                new VerificationResult(true, true, true, 8, List.of()));
        SpecFidelityReport firstReview = report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE);
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(firstReview).thenAnswer(invocation -> {
            cancelled.set(true);
            return SpecFidelityReport.empty();
        });
        when(service.preserveCandidate(any(), any(), anyString(), any())).thenReturn(mock(GenerationOutcome.class));
        GenerationAttemptLoop loop = newLoop(GenerationMode.GENERATE, 2, 1, false, cancelled::get);

        loop.run();

        ArgumentCaptor<GenerationAttemptLoop.CandidateSnapshot> preserved = ArgumentCaptor.forClass(GenerationAttemptLoop.CandidateSnapshot.class);
        verify(service).preserveCandidate(preserved.capture(), any(), anyString(), any());
        assertThat(preserved.getValue().verification().testCount()).isEqualTo(8);
        assertThat(preserved.getValue().producedFiles().get(RepositoryType.TESTS)).containsKey("test/ImprovedTest.java").doesNotContainKey("test/OriginalTest.java");
        assertThat(preserved.getValue().reviewReport().hasFindings()).isFalse();
    }

    @Test
    void unavailableRepairReviewPreservesThePreviouslyReviewedCandidate() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger testsExtraction = new AtomicInteger();
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any())).thenAnswer(invocation -> {
            String path = testsExtraction.getAndIncrement() == 0 ? "test/ReviewedTest.java" : "test/UnreviewedRepairTest.java";
            return new GenerationWorkspaceService.RepositoryExtraction(Map.of(path, "class Test {}"), false);
        });
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(new VerificationResult(true, true, true, 7, List.of()),
                new VerificationResult(true, true, true, 8, List.of()));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE)).thenAnswer(invocation -> {
                    cancelled.set(true);
                    return SpecFidelityReport.qualityReviewUnavailable("review interrupted");
                });
        when(service.preserveCandidate(any(), any(), anyString(), any())).thenReturn(mock(GenerationOutcome.class));
        GenerationAttemptLoop loop = newLoop(GenerationMode.GENERATE, 2, 1, false, cancelled::get);

        loop.run();

        ArgumentCaptor<GenerationAttemptLoop.CandidateSnapshot> preserved = ArgumentCaptor.forClass(GenerationAttemptLoop.CandidateSnapshot.class);
        verify(service).preserveCandidate(preserved.capture(), any(), anyString(), any());
        assertThat(preserved.getValue().verification().testCount()).isEqualTo(7);
        assertThat(preserved.getValue().producedFiles().get(RepositoryType.TESTS)).containsKey("test/ReviewedTest.java").doesNotContainKey("test/UnreviewedRepairTest.java");
        assertThat(preserved.getValue().reviewReport().findings()).noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    @Test
    void executedMutantEvidenceReconcilesOnlyItsExactTextHypothesis() {
        SpecFidelityReport.Finding targeted = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "reverse the complete list",
                "a text reviewer suspects this may pass");
        SpecFidelityReport.Finding paraphrase = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "preserve list identity",
                "a different hypothesis remains");
        SpecFidelityReport.Finding uncovered = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "dispatch after switching",
                "no assertion reaches the new strategy");
        ContractWitness counterexample = new ContractWitness("R1", "reverse", "@Test void reverse() {}", "reverses the list");
        SemanticMutant mutant = new SemanticMutant("R1", "src/D.java", "class D {}", "class D { int reverse; }", counterexample, targeted);
        SpecFidelityReport report = new SpecFidelityReport(List.of(targeted, paraphrase, uncovered));

        assertThat(SemanticEvidenceReconciler.reconcile(report, List.of(new SemanticMutantOutcome(mutant, Disposition.KILLED_BY_GRADED_SUITE)))).containsExactly(paraphrase,
                uncovered);
        assertThat(SemanticEvidenceReconciler.reconcile(report, List.of(new SemanticMutantOutcome(mutant, Disposition.INCONCLUSIVE)))).containsExactly(targeted, paraphrase,
                uncovered);
    }

    @Test
    void killedMutantDrainsItsExactUncoveredRequirementHypothesis() {
        SpecFidelityReport.Finding uncovered = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "dispatch after switching",
                "the reviewer suspects no assertion reaches the new strategy");
        ContractWitness counterexample = new ContractWitness("R4", "dispatchesAfterSwitch", "@Test void dispatchesAfterSwitch() {}", "keeps using the old strategy");
        SemanticMutant mutant = new SemanticMutant("R4", "src/Dispatcher.java", "class Dispatcher {}", "class Dispatcher { int old; }", counterexample, uncovered);

        assertThat(SemanticEvidenceReconciler.reconcile(new SpecFidelityReport(List.of(uncovered)), List.of(new SemanticMutantOutcome(mutant, Disposition.KILLED_BY_GRADED_SUITE))))
                .isEmpty();
    }

    @Test
    void blockingReviewStillExecutesAndDrainsAFalseOracleHypothesis() {
        sandbox.withFile(SPEC_PATH, "## Rules\n| R1 | choose the nearest value |");
        when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/NearestTest.java", "class NearestTest {}"), false));
        SpecFidelityReport.Finding weakOracle = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "equal distances may choose the later value",
                "the reviewer suspects the suite does not distinguish this behavior");
        SpecFidelityReport.Finding scaffoldGap = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "starter documentation is incomplete",
                "the TODO does not explain the contract");
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SpecFidelityReport(List.of(weakOracle, scaffoldGap)));
        ContractWitness counterexample = new ContractWitness("R1", "keepsEarlierTie", "@Test void keepsEarlierTie() {}", "chooses the later equal-distance value");
        SemanticMutant mutant = new SemanticMutant("R1", "src/Nearest.java", "class Nearest {}", "class Nearest { int later; }", counterexample, weakOracle);
        when(specFidelityCritic.authorSemanticMutants(anyString(), any(), any(), any(), any())).thenReturn(List.of(mutant));
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(mutant, Disposition.KILLED_BY_GRADED_SUITE)));

        GenerationAttemptLoop loop = newGenerateLoop(1, 0);
        loop.run();

        assertThat(loop.specFidelityReport().findings()).containsExactly(scaffoldGap);
        verify(specFidelityCritic).authorContractWitnesses(anyString(), anyString(), anyString(), any(), any());
    }

    private static SpecFidelityReport report(SpecFidelityReport.Kind... kinds) {
        return new SpecFidelityReport(
                List.of(kinds).stream().map(kind -> new SpecFidelityReport.Finding(kind, "requirement for " + kind.name(), "detail for " + kind.name())).toList());
    }

    private static SemanticMutant mutant(String ruleId, String testName, String wrongBehavior) {
        return new SemanticMutant(ruleId, "src/Scheduler.java", "class Scheduler {}", "class Scheduler { int " + testName + "; }",
                new ContractWitness(ruleId, testName, "@Test void " + testName + "() {}", wrongBehavior));
    }

    /** Every review round returns {@code review}, so the loop repairs until a budget stops it rather than because the findings ran out. */
    private void reviewAlwaysReturns(SpecFidelityReport review) {
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(review);
    }

    /** The writable roots of every repair scope the loop entered, in round order. */
    private List<Set<String>> enteredRepairScopes() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> roots = ArgumentCaptor.forClass(Set.class);
        verify(baseTools, Mockito.atLeast(0)).enterRepairScope(roots.capture());
        return roots.getAllValues();
    }

    @Nested
    class RepairSurfaceScheduling {

        static Stream<Arguments> surfaceSelection() {
            return Stream.of(
                    // A contract contradiction may reconcile every artifact, because the inconsistency it names can live in any of them.
                    Arguments.of("a contract blocker alone", report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION),
                            Set.of("solution", "template", "tests", "test-plan.json", "problem-statement.md")),
                    // A weak oracle is repaired by strengthening the tests, never by editing the solution it failed to distinguish.
                    Arguments.of("an oracle blocker alone", report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE), Set.of("tests", "test-plan.json", "problem-statement.md")),
                    // A template gap is repaired in the starter/reference scaffold, with the graded suite off limits.
                    Arguments.of("a scaffold blocker alone", report(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP), Set.of("solution", "template", "problem-statement.md")),
                    // Declaration order in RepairSurface is the priority order, so a contract blocker outranks both others.
                    Arguments.of("contract, oracle and scaffold blockers together",
                            report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE,
                                    SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP),
                            Set.of("solution", "template", "tests", "test-plan.json", "problem-statement.md")),
                    Arguments.of("oracle and scaffold blockers together", report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP),
                            Set.of("tests", "test-plan.json", "problem-statement.md")),
                    // An uncovered requirement is an oracle gap, not a contract one: the behaviour is agreed, only untested.
                    Arguments.of("an uncovered requirement", report(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT), Set.of("tests", "test-plan.json", "problem-statement.md")));
        }

        @ParameterizedTest(name = "{0} is repaired under that surface''s write barrier")
        @MethodSource("surfaceSelection")
        void theScheduledSurfacesWritableRootsAreTheOnesTheAgentGets(String scenario, SpecFidelityReport review, Set<String> expectedWritableRoots) {
            reviewAlwaysReturns(review);

            newGenerateLoop(3, 1).run();

            assertThat(enteredRepairScopes()).as(scenario).containsExactly(expectedWritableRoots);
        }

        @Test
        void anAdvisoryOnlyReviewSchedulesNoRepairAtAll() {
            // Nothing blocks, so the run is finished; entering a repair scope here would spend a round rewriting a candidate that already passed every gate.
            reviewAlwaysReturns(report(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, SpecFidelityReport.Kind.MISSING_FAILURE_MESSAGE));

            GenerationAttemptLoop loop = newGenerateLoop(3, 2);
            loop.run();

            verify(baseTools, never()).enterRepairScope(any());
            assertThat(loop.terminationReason()).isEqualTo(TerminationReason.CONVERGED);
        }

        @Test
        void anUnchangedSemanticRepairCannotRerollAwayThePreviousVerdict() {
            SpecFidelityReport blocker = report(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT);
            reviewAlwaysReturns(blocker);
            when(workspace.extractProblemStatement(any(), anyString())).thenReturn("UNCHANGED");

            GenerationAttemptLoop loop = newGenerateLoop(4, 2);
            loop.run();

            verify(specFidelityCritic, times(1)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            assertThat(loop.specFidelityReport()).isEqualTo(blocker);
            assertThat(progressLines).anyMatch(line -> line.contains("made no artifact changes") && line.contains("retaining the previous environment evidence"));
            assertThat(loop.terminationReason()).isEqualTo(TerminationReason.REPAIR_BUDGET_EXHAUSTED);
        }

        @Test
        void aSurfaceThatHasHeldTwoRoundsYieldsToOneThatHasNeverHadA() {
            // Both surfaces block on every round, so priority alone would give ORACLE the whole budget and ship the scaffold gap unrepaired. The loop's own consecutive-round
            // count is what makes the third round different from the first two.
            reviewAlwaysReturns(report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP));

            newGenerateLoop(5, 3).run();

            Set<String> oracleRoots = Set.of("tests", "test-plan.json", "problem-statement.md");
            Set<String> scaffoldRoots = Set.of("solution", "template", "problem-statement.md");
            assertThat(enteredRepairScopes()).containsExactly(oracleRoots, oracleRoots, scaffoldRoots);
        }

        @Test
        void aWitnessAdoptionRoundDoesNotSpendTheOracleSurfacesFairnessCredit() {
            // The first round is the one witness-adoption round: the candidate passes everything and the reviewer only offers a validated test. Blockers on two surfaces then
            // appear. Because adoption is not a repair, ORACLE arrives at its first genuine repair with a fresh consecutive count, so it holds two rounds before yielding —
            // recording adoption as a repaired round would make it yield after one and hand the scaffold its round early.
            when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty(),
                    report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP));
            sandbox.withFile(SPEC_PATH, "# Exercise\n\n## Rules\n- R1: computes a result.");
            when(workspace.extractRepository(any(), anyString(), Mockito.eq(RepositoryType.TESTS), any()))
                    .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/CalculatorTest.java", "class CalculatorTest {}"), false));
            ContractWitness witness = new ContractWitness("R1", "computesTheResult", "void computesTheResult() {}", "returns an incorrect result");
            when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(witness));
            when(verifier.evaluateContractWitnesses(any(), anyString(), any(), any(), any(), any()))
                    .thenReturn(List.of(new ContractWitnessOutcome(witness, ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED, "")));

            newGenerateLoop(6, 4).run();

            Set<String> oracleRoots = Set.of("tests", "test-plan.json", "problem-statement.md");
            Set<String> scaffoldRoots = Set.of("solution", "template", "problem-statement.md");
            assertThat(enteredRepairScopes()).containsExactly(oracleRoots, oracleRoots, oracleRoots, scaffoldRoots);
        }

        @Test
        void theRepairScopeIsLeftBehindEvenWhenTheAttemptItScopedIsTheLastOne() {
            // The barrier is entered before an attempt and must be gone after it: a scope that outlived its attempt would silently constrain the next agent call, or the
            // post-loop verify, to the previous round's surface.
            reviewAlwaysReturns(report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE));

            newGenerateLoop(2, 1).run();

            InOrder scope = Mockito.inOrder(baseTools);
            scope.verify(baseTools).enterRepairScope(Set.of("tests", "test-plan.json", "problem-statement.md"));
            scope.verify(baseTools).exitRepairScope();
            verify(baseTools, times(2)).exitRepairScope();
        }
    }

    @Nested
    class Budgets {

        @Test
        void theSemanticRepairBudgetBoundsTheRoundsExactly() {
            // The boundary the budget names: three rounds are granted and a fourth is not, even though blockers and attempts both remain.
            reviewAlwaysReturns(report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE));

            GenerationAttemptLoop loop = newGenerateLoop(10, 3);
            loop.run();

            verify(baseTools, times(3)).enterRepairScope(any());
            verify(agentLoopRunner, times(4)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
            assertThat(loop.terminationReason()).isEqualTo(TerminationReason.REPAIR_BUDGET_EXHAUSTED);
        }

        @Test
        void theAttemptCapStopsTheLoopBeforeTheRepairBudgetIsSpent() {
            // Two attempts and a budget of three: the cap is reached first, so the run is not out of repair rounds — it is out of attempts, and must say so.
            reviewAlwaysReturns(report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE));

            GenerationAttemptLoop loop = newGenerateLoop(2, 3);
            loop.run();

            verify(agentLoopRunner, times(2)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
            // Only the first attempt schedules a repair, and only the first attempt announces one. Checking the cap BEFORE scheduling is what the loop bound alone does not
            // give: a repair scheduled on the final attempt can never run, so announcing it tells the instructor the AI is correcting the exercise while the run is ending.
            verify(baseTools, times(1)).enterRepairScope(any());
            assertThat(progressLines).filteredOn(line -> line.contains("asking the AI to correct them")).hasSize(1);
            assertThat(loop.terminationReason()).isEqualTo(TerminationReason.ATTEMPT_CAP_REACHED);
        }

        @Test
        void everyRepairStartsFromCurrentWorkspaceStateWithoutReplayingTheFailedConversation() {
            reviewAlwaysReturns(report(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE));

            newGenerateLoop(3, 2).run();

            verify(agentLoopRunner, times(3)).runSession(anyString(), isNull(), anyString(), any(), anyInt(), any(), any(), any());
        }

        @Test
        void aNonPositiveAttemptCapRunsNothingAndStillNamesItsExit() {
            // Only reachable by misconfiguration, which is exactly when a run's artifact must still say why it did nothing rather than carry no reason at all.
            GenerationAttemptLoop loop = newGenerateLoop(0, 3);

            assertThat(loop.run()).as("the caller resolves the outcome from the loop's final state, which here is untouched").isNull();

            verify(agentLoopRunner, never()).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
            verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
            assertThat(loop.terminationReason()).isEqualTo(TerminationReason.ATTEMPT_CAP_REACHED);
        }

        @Test
        void aMechanicallyRejectedCandidateNeverEntersARepairScope() {
            // The mechanical repair phase asks the agent to fix the build against the verifier's own reasons; scoping it to a semantic surface would forbid the very files the
            // rejection names.
            when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("the template passed a graded test"));
            when(workspace.extractProblemStatement(any(), anyString())).thenReturn("attempt 1", "attempt 2", "attempt 3", "attempt 4");

            GenerationAttemptLoop loop = newGenerateLoop(10, 3);
            loop.run();

            verify(baseTools, never()).enterRepairScope(any());
            verify(specFidelityCritic, never()).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            assertThat(loop.terminationReason()).isEqualTo(TerminationReason.MECHANICAL_REPAIR_EXHAUSTED);
        }
    }
}
