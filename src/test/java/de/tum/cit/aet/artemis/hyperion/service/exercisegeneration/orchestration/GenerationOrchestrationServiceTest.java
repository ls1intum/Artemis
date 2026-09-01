package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionGenerationConfigurationValidator;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
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

    private static SeededStructuralTests structuralTests(String... names) {
        return new SeededStructuralTests(Set.of(names), Map.of("test/de/tum/cit/aet/artemis/TrustedStructuralTest.java", "// server-owned test fixture"));
    }

    private static final int MAX_MECHANICAL_ATTEMPTS = GenerationAttemptLoop.MAX_MECHANICAL_ATTEMPTS;

    private InteractiveSandbox sandbox;

    private AgentLoopRunner agentLoopRunner;

    private DifferentialVerificationService verifier;

    private StructuralOracleSeedingService structuralOracleSeeder;

    private SpecFidelityCriticService specFidelityCritic;

    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    private GenerationWorkspaceService workspace;

    private AgentSystemPromptService systemPromptService;

    private GenerationJobService jobService;

    private StagedGenerationRunner stagedGenerationRunner;

    private StageCheckService stageCheckService;

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
        stagedGenerationRunner = mock(StagedGenerationRunner.class);
        stageCheckService = mock(StageCheckService.class);

        when(sandbox.createSession(any())).thenReturn(SESSION_ID);
        when(systemPromptService.build(any(), any())).thenReturn("SYSTEM_PROMPT");
        AtomicInteger defaultExtraction = new AtomicInteger();
        when(workspace.extractRepository(any(), anyString(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2) == RepositoryType.SOLUTION
                ? new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Attempt.java", "attempt " + defaultExtraction.incrementAndGet()), false)
                : new GenerationWorkspaceService.RepositoryExtraction(Map.of(), false));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("PROBLEM STATEMENT");
        when(workspace.seedWorkspace(any(), anyString(), any(), any(), anyBoolean())).thenReturn(new GenerationWorkspaceService.WorkspaceSeed(Map.of(), Map.of()));
        // Mirrors the real predicate's length threshold; a plain mock would report every fixture statement as non-authoritative.
        when(systemPromptService.isAuthoritativeProblemStatement(any())).thenAnswer(invocation -> {
            String statement = ((ProgrammingExercise) invocation.getArgument(0)).getProblemStatement();
            return statement != null && statement.strip().length() >= 40;
        });
        when(verifier.checkBuildEnvironment(any(), anyString(), any())).thenReturn(Optional.empty());
        when(structuralOracleSeeder.seedIfStructuralDiff(any(), anyString(), any())).thenReturn(SeededStructuralTests.EMPTY);
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());
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
        return newService(false);
    }

    private GenerationOrchestrationService newService(boolean stagedGenerationEnabled) {
        return new GenerationOrchestrationService(Optional.of(sandbox), workspace, agentLoopRunner, verifier, systemPromptService, structuralOracleSeeder, specFidelityCritic,
                jobService, Optional.of(testCaseRepository), 100, 6, stagedGenerationRunner, stagedGenerationEnabled, stageCheckService, new AgentTranscriptWriter(""),
                new de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry());
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

    private GenerationOutcome generate(BooleanSupplier cancelled) {
        return service.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, cancelled, null, null, null);
    }

    @Test
    void destroyingSessionForgetsFrozenStructuralOracle() {
        service.destroyQuietly(null, SESSION_ID);

        verify(structuralOracleSeeder).forget(SESSION_ID);
    }

    /** Without a non-empty TEMPLATE baseline, "could not be read" and "legitimately produced nothing" are the same empty map and the extraction-failed guard is unobservable. */
    private void seedNonEmptyTemplateBaseline() {
        when(workspace.seedWorkspace(any(), anyString(), any(), any(), anyBoolean())).thenReturn(
                new GenerationWorkspaceService.WorkspaceSeed(Map.of(), Map.of(), Map.of(), Map.of(RepositoryType.TEMPLATE, Map.of("src/Given.java", "class Given {}"))));
    }

    private void makeSolutionChangeOnEachExtraction() {
        AtomicInteger extraction = new AtomicInteger();
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.SOLUTION), any()))
                .thenAnswer(invocation -> new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Attempt.java", "attempt " + extraction.incrementAndGet()), false));
    }

    private void makeAllExtractionsEmpty() {
        when(workspace.extractRepository(any(), anyString(), any(), any())).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of(), false));
    }

    @Test
    void rejectedThenAccepted_feedsReportIntoNextPromptAndAccepts() {
        makeSolutionChangeOnEachExtraction();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("template unexpectedly passed all tests"),
                accepted());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).as("the second, accepted attempt yields an accepted outcome").isTrue();
        }

        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts.get(0)).as("the first prompt carries the instructor's source requirements without a model-invented intermediary").contains("PRIMARY SOURCE REQUIREMENTS",
                "Build a bubble sort exercise.");
        assertThat(prompts.get(1)).as("the second prompt carries the verifier's rejection report so the agent can fix exactly those issues")
                .contains("template unexpectedly passed all tests", "rejected by the differential verifier", "PRIMARY SOURCE REQUIREMENTS", "Build a bubble sort exercise.")
                .contains("smallest coherent repair");
    }

    @Test
    void buildEnvironmentFailureStopsBeforeTheFirstProviderAttempt() {
        when(verifier.checkBuildEnvironment(sandbox, SESSION_ID, exercise)).thenReturn(Optional.of("The sandbox image is not offline-ready."));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.ERROR);
            assertThat(outcome.errorMessage()).contains("not offline-ready");
        }

        verify(agentLoopRunner, never()).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
        verify(sandbox).destroySession(SESSION_ID);
    }

    @Test
    void buildEnvironmentFixtureStagingFailureStopsBeforeTheFirstProviderAttempt() {
        doThrow(new IllegalStateException("Could not create /opt/hyperion-readiness-fixture; Authorization: Bearer secret-value " + "details ".repeat(1_000))).when(workspace)
                .stageBuildReadinessFixture(sandbox, SESSION_ID, exercise);
        Logger logger = (Logger) LoggerFactory.getLogger(GenerationOrchestrationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            try (GenerationOutcome outcome = generate(() -> false)) {
                assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.ERROR);
                assertThat(outcome.errorMessage()).contains("could not be prepared", "authoring agent was not started").doesNotContain("secret-value");
            }

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("Could not create /opt/hyperion-readiness-fixture", "[REDACTED]", "[truncated]")
                    .doesNotContain("secret-value").hasSizeLessThanOrEqualTo(4_500);
        }
        finally {
            logger.detachAppender(appender);
        }

        verify(agentLoopRunner, never()).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, never()).checkBuildEnvironment(any(), anyString(), any());
        verify(sandbox).destroySession(SESSION_ID);
    }

    @Test
    void acceptedOnFirstAttempt_runsAgentExactlyOnce() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(agentLoopRunner, times(1)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(1)).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
    }

    @Test
    void stagedGenerationEnabled_generateJava_delegatesToStagedGenerationRunnerInsteadOfTheSingleAgentLoopCall() {
        GenerationOrchestrationService stagedService = newService(true);
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(),
                any())).thenReturn(new StagedGenerationRunner.StagedRunOutcome(completed(), null));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = stagedService.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, () -> false, null, null, null)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(stagedGenerationRunner, times(1)).run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any(), any());
        verify(agentLoopRunner, never()).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void sourceBrief_runsSpecStageWithoutLettingAnIntermediateDraftAnchorTheSpecification() {
        GenerationOrchestrationService stagedService = newService(true);
        String draft = "# Draft playlist exercise\n\nThis generated draft is long enough to look authoritative but may have omitted explicit requirements.";
        String sourceBrief = "Teach Strategy with three playlist strategies. Students must create the interface. Include a UML diagram.";
        when(exercise.getProblemStatement()).thenReturn(draft);
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(),
                any())).thenReturn(new StagedGenerationRunner.StagedRunOutcome(completed(), null));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> rawBrief = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> specStageApplies = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> conceptSelectionApplies = ArgumentCaptor.forClass(Boolean.class);

        try (GenerationOutcome outcome = stagedService.generate(exercise, user, "resolved instruction", JOB_ID, GenerationMode.GENERATE, () -> false, null, null, null,
                sourceBrief)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(stagedGenerationRunner).run(any(), any(), any(), prompt.capture(), rawBrief.capture(), any(), any(), anyString(), any(), any(), any(), any(),
                specStageApplies.capture(), conceptSelectionApplies.capture(), any(), any());
        assertThat(specStageApplies.getValue()).isTrue();
        assertThat(conceptSelectionApplies.getValue()).isTrue();
        assertThat(prompt.getValue()).contains("PRIMARY SOURCE REQUIREMENTS", sourceBrief).doesNotContain("CURRENT AI-GENERATED DRAFT", draft);
        assertThat(rawBrief.getValue()).isEqualTo(sourceBrief);
        verify(workspace).seedWorkspace(any(), anyString(), eq(exercise), eq(GenerationMode.GENERATE), eq(false));
    }

    @Test
    void authoritativeStatement_compilesReviewedSpecWithoutInventingACompetingConcept() {
        GenerationOrchestrationService stagedService = newService(true);
        String statement = "# Elevator dispatch\n\nUse the supplied strategies to choose one reachable request globally, with deterministic ties.";
        when(exercise.getProblemStatement()).thenReturn(statement);
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(),
                any())).thenReturn(new StagedGenerationRunner.StagedRunOutcome(completed(), null));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        ArgumentCaptor<String> rawBrief = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> specStageApplies = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> conceptSelectionApplies = ArgumentCaptor.forClass(Boolean.class);

        try (GenerationOutcome outcome = stagedService.generate(exercise, user, "", JOB_ID, GenerationMode.GENERATE, () -> false, null, null, null)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(stagedGenerationRunner).run(any(), any(), any(), anyString(), rawBrief.capture(), any(), any(), anyString(), any(), any(), any(), any(), specStageApplies.capture(),
                conceptSelectionApplies.capture(), any(), any());
        assertThat(specStageApplies.getValue()).isTrue();
        assertThat(conceptSelectionApplies.getValue()).isFalse();
        assertThat(rawBrief.getValue()).contains("STARTING PROBLEM STATEMENT", statement);
        verify(workspace).seedWorkspace(any(), anyString(), eq(exercise), eq(GenerationMode.GENERATE), eq(true));
    }

    @Test
    void stagedGenerationDisabled_generateJava_usesTheSingleAgentLoopCallDirectly() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(agentLoopRunner, times(1)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(stagedGenerationRunner, never()).run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(),
                anyBoolean(), any());
    }

    @Test
    void adaptMode_passesThePersistedGradedTestNamesAsTheTotalWipeBaseline() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(testCaseRepository.findByExerciseId(42L)).thenReturn(Set.of(testCase("evictsLeastRecentlyUsed"), testCase("capacityIsRespected")));

        try (GenerationOutcome outcome = service.generate(exercise, user, "Tighten the eviction test.", JOB_ID, GenerationMode.ADAPT, () -> false, null, null, null)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        ArgumentCaptor<VerificationRequest> requestCaptor = ArgumentCaptor.forClass(VerificationRequest.class);
        verify(verifier).verify(any(), anyString(), any(), requestCaptor.capture(), any(Runnable.class));
        verify(workspace).seedWorkspace(eq(sandbox), eq(SESSION_ID), eq(exercise), eq(GenerationMode.ADAPT), anyBoolean());
        assertThat(requestCaptor.getValue().baselineGradedTestNames()).as("ADAPT hands the persisted graded test names to the total-wipe gate")
                .containsExactlyInAnyOrder("evictsLeastRecentlyUsed", "capacityIsRespected");
    }

    @Test
    void generateMode_passesAnEmptyTotalWipeBaselineAndDoesNotQueryPersistedTests() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(workspace).seedWorkspace(eq(sandbox), eq(SESSION_ID), eq(exercise), eq(GenerationMode.GENERATE), anyBoolean());

        ArgumentCaptor<VerificationRequest> requestCaptor = ArgumentCaptor.forClass(VerificationRequest.class);
        verify(verifier).verify(any(), anyString(), any(), requestCaptor.capture(), any(Runnable.class));
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
        makeSolutionChangeOnEachExtraction();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("still failing"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).as("an exercise rejected on every attempt is not accepted").isFalse();
        }

        verify(agentLoopRunner, times(MAX_MECHANICAL_ATTEMPTS)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(MAX_MECHANICAL_ATTEMPTS)).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
    }

    @Test
    void unchangedRejectedCandidateIsNotVerifiedAgain() {
        makeAllExtractionsEmpty();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("still failing"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isFalse();
        }

        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
    }

    @Test
    void unchangedArtifactsAreVerifiedAgainWhenStructuralAuthorityChanges() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(structuralOracleSeeder.seedIfStructuralDiff(any(), anyString(), any())).thenReturn(SeededStructuralTests.EMPTY, structuralTests("StructuralTest"));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("missing structural authority"), accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(verifier, times(2)).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
    }

    @Test
    void cancellationAfterExtractingAnUnchangedRetryWinsOverDuplicateDetection() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger solutionExtractions = new AtomicInteger();
        when(exercise.getProblemStatement()).thenReturn("PROBLEM STATEMENT");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.SOLUTION), any())).thenAnswer(invocation -> {
            if (solutionExtractions.incrementAndGet() == 2) {
                cancelled.set(true);
            }
            return new GenerationWorkspaceService.RepositoryExtraction(Map.of(), false);
        });
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("still failing"));

        try (GenerationOutcome outcome = generate(cancelled::get)) {
            assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        }

        verify(verifier).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
    }

    @Test
    void cancelledLoopResult_skipsVerificationAndDestroysSession() {
        makeAllExtractionsEmpty();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any()))
                .thenReturn(loopSession(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 1, "")));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("");

        GenerationOutcome outcome = generate(() -> false);

        assertThat(outcome.isMechanicallyVerified()).isFalse();
        verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
        verify(sandbox).destroySession(SESSION_ID);
    }

    @Test
    void cancellationBeforeSandboxCreation_skipsProviderAndSandboxWork() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("");
        BooleanSupplier cancelled = () -> true;

        GenerationOutcome outcome = generate(cancelled);

        assertThat(outcome.isMechanicallyVerified()).isFalse();
        verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
        org.mockito.Mockito.verifyNoInteractions(sandbox);
        org.mockito.Mockito.verifyNoInteractions(agentLoopRunner);
    }

    @Test
    void thrownExceptionFromLoop_destroysSessionAndPropagates() {
        makeAllExtractionsEmpty();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenThrow(new RuntimeException("model exploded"));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("");

        assertThatThrownBy(() -> generate(() -> false)).isInstanceOf(RuntimeException.class).hasMessageContaining("model exploded");

        verify(sandbox, atLeastOnce()).destroySession(SESSION_ID);
        verify(verifier, never()).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
    }

    @Test
    void erroredLoop_capturesChangedWorkspaceForDiagnostics() {
        when(exercise.getProblemStatement()).thenReturn("Original statement");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any()))
                .thenReturn(loopSession(new AgentLoopResult(AgentLoopResult.Status.ERROR, 4, "Provider stopped responding")));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("Improved draft statement");
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Library.java", "class Library {}"), false));

        GenerationOutcome outcome = generate(() -> false);

        assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(outcome.hasCapturedArtifacts()).isTrue();
        assertThat(outcome.producedProblemStatement()).isEqualTo("Improved draft statement");
        assertThat(outcome.producedFiles(RepositoryType.SOLUTION)).containsKey("src/Library.java");
        verify(sandbox, never()).destroySession(SESSION_ID);
        outcome.close();
        verify(sandbox).destroySession(SESSION_ID);
    }

    @Test
    void erroredLoop_preservesSuccessfulRepositoryExtractionsWhenAnotherRepositoryCannotBeRead() {
        seedNonEmptyTemplateBaseline();
        when(exercise.getProblemStatement()).thenReturn("Original statement");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any()))
                .thenReturn(loopSession(new AgentLoopResult(AgentLoopResult.Status.ERROR, 4, "Provider stopped responding")));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("Original statement");
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Library.java", "class Library {}"), false));
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.TEMPLATE), any())).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of(), true));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.hasCapturedArtifacts()).isTrue();
            assertThat(outcome.producedProblemStatement()).isEqualTo("Original statement");
            assertThat(outcome.producedFiles(RepositoryType.SOLUTION)).containsEntry("src/Library.java", "class Library {}");
            assertThat(outcome.producedFiles(RepositoryType.TEMPLATE)).isEmpty();
        }
    }

    @Test
    void erroredLoopWithUnchangedStatementAndIncompleteExtractionDoesNotInventCapturedArtifacts() {
        // TEMPLATE held content at seed time and cannot be read now: dropping the extraction-failed guard would report it as an emptied — that is, changed — repository.
        makeAllExtractionsEmpty();
        seedNonEmptyTemplateBaseline();
        when(exercise.getProblemStatement()).thenReturn("Original statement");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any()))
                .thenReturn(loopSession(new AgentLoopResult(AgentLoopResult.Status.ERROR, 4, "Provider stopped responding")));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("Original statement");
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.TEMPLATE), any())).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of(), true));

        GenerationOutcome outcome = generate(() -> false);

        assertThat(outcome.hasCapturedArtifacts()).isFalse();
        verify(sandbox).destroySession(SESSION_ID);
    }

    @Test
    void acceptedPath_handsTheSeededStructuralNamesToTheVerifier() {
        // The verifier can only require the seeded grading checks to appear in the student checklist against the names this run seeded; an empty set silently disables the gate.
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(structuralOracleSeeder.seedIfStructuralDiff(eq(sandbox), eq(SESSION_ID), eq(exercise))).thenReturn(structuralTests("testMethods[Strategy]", "testClass[Strategy]"));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(4, Runnable.class).run();
            return accepted();
        });

        try (GenerationOutcome ignored = generate(() -> false)) {
            ArgumentCaptor<VerificationRequest> request = ArgumentCaptor.forClass(VerificationRequest.class);
            verify(verifier).verify(eq(sandbox), eq(SESSION_ID), eq(exercise), request.capture(), any(Runnable.class));
            assertThat(request.getValue().seededStructuralTestNames()).containsExactlyInAnyOrder("testMethods[Strategy]", "testClass[Strategy]");
        }
    }

    private static SpecFidelityReport reportWith(String... requirements) {
        return new SpecFidelityReport(Stream.of(requirements)
                .map(requirement -> new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, requirement, "no test covers it")).toList());
    }

    private static SpecFidelityReport advisoryReportWith(String requirement) {
        return new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, requirement, "an example would improve clarity")));
    }

    @Test
    void acceptedWithAdvisoryPresentationFinding_doesNotSpendRetryOrFlipVerdict() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(advisoryReportWith("state rollback"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).as("an oracle-accepted exercise stays accepted with advisory findings").isTrue();
            assertThat(outcome.specFidelityReport().hasFindings()).isTrue();
        }
        verify(agentLoopRunner).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void acceptedAdaptationWithOnlyAdvisoryFindings_staysAccepted() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(advisoryReportWith("rollback interaction"));

        try (GenerationOutcome outcome = service.generate(exercise, user, "Change remove only and preserve everything else", "job", GenerationMode.ADAPT, () -> false, null, null,
                response -> {
                })) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().hasFindings()).isTrue();
        }
        verify(agentLoopRunner).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void acceptedAdaptationWithPersistentScopeDriftRemainsMechanicallyAcceptedAndRequiresReview() {
        SpecFidelityReport scopeDrift = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE,
                "solution/src/Inventory.java removed displayName(String)", "The feedback explicitly required preserving it.")));
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(scopeDrift);

        try (GenerationOutcome outcome = service.generate(exercise, user, "Change only remove; preserve displayName", "job", GenerationMode.ADAPT, () -> false, null, null,
                response -> {
                })) {
            assertThat(outcome.isMechanicallyVerified()).as("mechanically valid work is saved canonically so the instructor can review the scope finding").isTrue();
            assertThat(outcome.specFidelityReport().hasBlockingFindings()).isTrue();
        }
        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void acceptedAdaptationWithCorrectableScopeFinding_retriesAndAcceptsTheRepair() {
        String feedback = "Reject zero quantities and preserve this full instructor context: " + "context ".repeat(40);
        SpecFidelityReport missingChange = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING,
                "reject zero quantities", "The candidate does not add the requested validation.")));
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()), loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted(), accepted());
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(missingChange, SpecFidelityReport.empty());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome outcome = service.generate(exercise, user, feedback, "job", GenerationMode.ADAPT, () -> false, null, null, response -> {
        })) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        assertThat(promptCaptor.getAllValues().get(1)).contains("Requested adaptation change missing or incomplete", "reject zero quantities", feedback);
    }

    @Test
    void unchangedAdaptationIsRejectedAsMissingTheRequestedChange() {
        makeAllExtractionsEmpty();
        when(exercise.getProblemStatement()).thenReturn("PROBLEM STATEMENT");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING, "Change one method only", "The candidate is unchanged."))));

        try (GenerationOutcome outcome = service.generate(exercise, user, "Change one method only", JOB_ID, GenerationMode.ADAPT, () -> false, null, null, null)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().findings()).singleElement()
                    .satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING));
        }

        verify(specFidelityCritic).critiqueAdaptation(contains("RUN INSTRUCTION (authoritative adaptation request):\nChange one method only"), eq("PROBLEM STATEMENT"), any(),
                eq(""), any(), any(), any(), any());
        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(specFidelityCritic, never()).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** An adaptation whose change summary is far too large to review in one pass, so the bounded rendering truncates it. */
    private void stageAdaptationWithOversizedChangeSummary() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(workspace.seedWorkspace(any(), anyString(), any(), any(), anyBoolean())).thenReturn(
                new GenerationWorkspaceService.WorkspaceSeed(Map.of(), Map.of(), Map.of(), Map.of(RepositoryType.SOLUTION, Map.of("src/Huge.java", "x".repeat(30_000)))));
    }

    @Test
    void adaptationCriticException_requiresInstructorReview() {
        stageAdaptationWithOversizedChangeSummary();
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new RuntimeException("critic plumbing failed"));

        try (GenerationOutcome outcome = service.generate(exercise, user, "Change one method only", "job", GenerationMode.ADAPT, () -> false, null, null, response -> {
        })) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().hasBlockingFindings()).isTrue();
        }
    }

    @Test
    void truncatedAdaptationEvidence_requiresInstructorReviewEvenWhenTheCriticReportsNothing() {
        // A clean report over evidence the critic could only partly see must not be reported as a fully reviewed adaptation.
        stageAdaptationWithOversizedChangeSummary();
        when(specFidelityCritic.critiqueAdaptation(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());

        try (GenerationOutcome outcome = service.generate(exercise, user, "Change one method only", "job", GenerationMode.ADAPT, () -> false, null, null, response -> {
        })) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().findings()).singleElement().satisfies(finding -> {
                assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.ADAPTATION_SCOPE_REVIEW_UNAVAILABLE);
                assertThat(finding.detail()).contains("truncated");
            });
        }
    }

    @Test
    void mechanicalRejectionIsRepairedBeforeSpendingAQualityReviewCall() {
        makeSolutionChangeOnEachExtraction();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("template passed a test"), accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
        }

        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        String retryPrompt = promptCaptor.getAllValues().get(1);
        assertThat(retryPrompt).as("the retry prompt still carries the hard rejection").contains("rejected by the differential verifier").contains("template passed a test");
        assertThat(retryPrompt).doesNotContain("Exercise-quality issues");
        verify(specFidelityCritic).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void lastMechanicalAttemptStillLeavesRoomForOneSemanticRepair() {
        makeSolutionChangeOnEachExtraction();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("first mechanical defect"),
                rejected("second mechanical defect"), rejected("third mechanical defect"), accepted(), accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("generic theme"),
                SpecFidelityReport.empty());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().hasBlockingFindings()).isFalse();
        }

        verify(agentLoopRunner, times(5)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(5)).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
        verify(specFidelityCritic, times(2)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void acceptedCandidateWithContractBlockerRetriesAndAcceptsTheRepair() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("emoji"), SpecFidelityReport.empty());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        assertThat(promptCaptor.getAllValues().get(1)).contains("automated full-artifact review", "review blockers", "Exercise-quality issues", "emoji")
                .contains("Keep the already verified solution unchanged", "test-controlled fake or recording collaborator", "unique sentinel")
                .contains("Do not add production caching", "solely to make a new oracle test pass").doesNotContain("acceptance blockers");
    }

    @Test
    void semanticRepairAddressesOneCoherentFindingSurfaceAtATime() {
        SpecFidelityReport mixedReview = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.INVENTED_REQUIREMENT, "null inputs", "not in the brief"),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "delegation", "does not prove forwarding"),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "starter docs", "missing point-of-use documentation")));
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(mixedReview, SpecFidelityReport.empty());

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
        }

        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), prompts.capture(), any(), anyInt(), any(), any(), any());
        assertThat(prompts.getAllValues().get(1)).contains("null inputs").doesNotContain("does not prove forwarding", "missing point-of-use documentation");
    }

    @Test
    void repairThatTradesOneBlockerForAnotherRetainsTheReviewedPredecessor() {
        SpecFidelityReport originalReview = reportWith("original contract blocker");
        SpecFidelityReport repairedReview = reportWith("repair introduced a different blocker");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(originalReview, repairedReview);
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Original verified candidate", "# First repair", "# Complete repair");

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.producedProblemStatement()).isEqualTo("# Original verified candidate");
            assertThat(outcome.specFidelityReport()).isEqualTo(originalReview);
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.REPAIR_DID_NOT_IMPROVE);
        }

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), prompts.capture(), any(), anyInt(), any(), any(), any());
        assertThat(prompts.getAllValues().get(1)).contains("original contract blocker");
        verify(specFidelityCritic, times(2)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void semanticReviewContinuitySurvivesTheAllowedMechanicalCorrection() {
        SpecFidelityReport originalReview = reportWith("the transition oracle is too weak");
        makeSolutionChangeOnEachExtraction();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted(), rejected("repair no longer compiles"),
                accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(originalReview, SpecFidelityReport.empty());
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Original", "# Broken repair", "# Corrected repair");

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().hasBlockingFindings()).isFalse();
        }

        ArgumentCaptor<SpecFidelityReport> previousReview = ArgumentCaptor.forClass(SpecFidelityReport.class);
        ArgumentCaptor<String> repairDelta = ArgumentCaptor.forClass(String.class);
        verify(specFidelityCritic, times(2)).critique(any(), any(), any(), any(), any(), any(), previousReview.capture(), any(), repairDelta.capture(), any(), any());
        assertThat(previousReview.getAllValues().get(1)).isEqualTo(originalReview);
        assertThat(repairDelta.getAllValues().get(1)).contains("Original", "Corrected repair", "Attempt.java");
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(agentLoopRunner, times(3)).runSession(anyString(), any(), prompts.capture(), any(), anyInt(), any(), any(), any());
        assertThat(prompts.getAllValues().get(2)).contains("audit the new assertion against the frozen contract", "fix or remove the unsupported assertion first",
                "production caching", "solely to make a new oracle test pass");
    }

    @Test
    void reviewAndRepairKeepTheStartingProblemStatementAsInstructorEvidence() {
        String startingStatement = "# Checkout summaries\n\nKeep due dates, return dates, invalid-duration handling, and per-member fee summaries.";
        when(exercise.getProblemStatement()).thenReturn(startingStatement);
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("invalid durations"),
                SpecFidelityReport.empty());

        ArgumentCaptor<String> reviewBrief = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> agentPrompt = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(specFidelityCritic, times(2)).critique(reviewBrief.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), agentPrompt.capture(), any(), anyInt(), any(), any(), any());
        assertThat(reviewBrief.getAllValues()).allSatisfy(brief -> assertThat(brief).contains("RUN INSTRUCTION", "STARTING PROBLEM STATEMENT", startingStatement));
        assertThat(agentPrompt.getAllValues().get(1)).contains("Preserve the mechanically correct work", "STARTING PROBLEM STATEMENT", startingStatement);
    }

    @Test
    void repairRegressionReturnsTheLastMechanicallyVerifiedCandidate() {
        SpecFidelityReport contractBlocker = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, "invalid events",
                "The statement and tests disagree about whether invalid events are ignored.")));
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted(), rejected("repair no longer compiles"),
                rejected("repair still does not compile"));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(contractBlocker);
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Mechanically verified candidate", "# Broken repair", "# Still broken repair");
        AtomicInteger specReads = new AtomicInteger();
        AtomicInteger planReads = new AtomicInteger();
        when(sandbox.exec(eq(SESSION_ID), any(), eq("cat"), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(3);
            if (path.endsWith("/SPEC.md")) {
                return new SandboxExecResultDTO(0, specReads.getAndIncrement() == 0 ? "# Verified spec" : "# Broken repair spec", "", false);
            }
            if (path.endsWith("/test-plan.json")) {
                return new SandboxExecResultDTO(0, planReads.getAndIncrement() == 0 ? "{\"tests\":[{\"name\":\"testGood\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}"
                        : "{\"tests\":[{\"name\":\"testBroken\",\"seamWeightTier\":1,\"visibility\":\"ALWAYS\"}]}", "", false);
            }
            return new SandboxExecResultDTO(1, "", "not found", false);
        });

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.verification()).isEqualTo(accepted());
            assertThat(outcome.producedProblemStatement()).isEqualTo("# Mechanically verified candidate");
            assertThat(outcome.specFidelityReport()).isEqualTo(contractBlocker);
            assertThat(outcome.specDocument()).isEqualTo("# Verified spec");
            assertThat(outcome.testPlanJson()).contains("testGood").doesNotContain("testBroken");
        }
    }

    @Test
    void exceptionDuringSemanticRepairReturnsTheLastMechanicallyVerifiedCandidate() {
        SpecFidelityReport contractBlocker = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, "invalid events",
                "The statement and tests disagree about whether invalid events are ignored.")));
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(contractBlocker);
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Mechanically verified candidate");
        when(structuralOracleSeeder.seedIfStructuralDiff(any(), anyString(), any())).thenReturn(SeededStructuralTests.EMPTY)
                .thenThrow(new IllegalStateException("repair extraction failed"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.verification()).isEqualTo(accepted());
            assertThat(outcome.producedProblemStatement()).isEqualTo("# Mechanically verified candidate");
            assertThat(outcome.specFidelityReport()).isEqualTo(contractBlocker);
        }
    }

    @Test
    void cancellationDuringSemanticRepairReturnsTheLastMechanicallyVerifiedCandidate() {
        SpecFidelityReport contractBlocker = reportWith("invalid events");
        AgentLoopResult cancelledRepair = new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 2, "cancelled");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()), loopSession(cancelledRepair));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(contractBlocker);
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Mechanically verified candidate");

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.loopResult()).isEqualTo(completed());
            assertThat(outcome.producedProblemStatement()).isEqualTo("# Mechanically verified candidate");
            assertThat(outcome.specFidelityReport()).isEqualTo(contractBlocker);
        }
    }

    @Test
    void cancellationAfterRepairVerificationKeepsTheLastReviewedCheckpoint() {
        SpecFidelityReport originalReview = reportWith("invalid events");
        AtomicBoolean cancelled = new AtomicBoolean();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted()).thenAnswer(invocation -> {
            cancelled.set(true);
            return accepted();
        });
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(originalReview);
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Reviewed candidate", "# Unreviewed repair");

        try (GenerationOutcome outcome = generate(cancelled::get)) {
            assertThat(outcome.producedProblemStatement()).isEqualTo("# Reviewed candidate");
            assertThat(outcome.specFidelityReport()).isEqualTo(originalReview);
        }

        verify(specFidelityCritic).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void criticThrows_runFailsClosedWithoutRetryingAnUnchangedCandidate() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new RuntimeException("critic exploded"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().findings()).singleElement()
                    .satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE));
        }
        verify(agentLoopRunner).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void partialCriticFailureStillRepairsActionableFindingsFromTheAvailablePass() {
        SpecFidelityReport partialReview = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.INVENTED_REQUIREMENT, "null input throws", "The instructor did not request null handling."),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE, "Exercise quality could not be verified",
                                "The test-oracle reviewer returned no verdict.")));
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(partialReview, SpecFidelityReport.empty());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }
        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void reviewProgressDistinguishesBlockingFindingsFromAdvisories() {
        SpecFidelityReport mixedReview = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "boundary", "no boundary assertion"),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, "workflow", "an example would help")));
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(mixedReview);
        List<String> progress = new java.util.ArrayList<>();

        try (GenerationOutcome ignored = service.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, () -> false, progress::add, null,
                null)) {
        }

        assertThat(progress).anySatisfy(message -> assertThat(message).contains("1 blocking", "1 advisory").doesNotContain("2 blocking"));
    }

    @Test
    void critic_isFedTaskBoundTestNamesAndTheExactVerifiedArtifacts() {
        String testPlan = "{\"tests\":[{\"name\":\"test_sort\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(sandbox.exec(eq(SESSION_ID), any(), eq("cat"), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(3);
            return path.endsWith("/test-plan.json") ? new SandboxExecResultDTO(0, testPlan, "", false) : new SandboxExecResultDTO(1, "", "not found", false);
        });
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("Intro.\n[task][Sort](test_sort,test_empty)\n[task][Edge](test_negative)");
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Sort.java", "solution"), false));
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.TEMPLATE), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Sort.java", "template"), false));
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/SortTest.java", "tests"), false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> namesCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<RepositoryType, Map<String, String>>> artifactsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> testPlanCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
        }

        verify(specFidelityCritic).critique(anyString(), anyString(), namesCaptor.capture(), artifactsCaptor.capture(), any(), any(), any(), any(), any(), testPlanCaptor.capture(),
                any());
        assertThat(namesCaptor.getValue()).containsExactly("test_sort", "test_empty", "test_negative");
        assertThat(artifactsCaptor.getValue()).containsEntry(RepositoryType.SOLUTION, Map.of("src/Sort.java", "solution"))
                .containsEntry(RepositoryType.TEMPLATE, Map.of("src/Sort.java", "template")).containsEntry(RepositoryType.TESTS, Map.of("test/SortTest.java", "tests"));
        assertThat(testPlanCaptor.getValue()).isEqualTo(testPlan);
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
    void generationRepairDeltaIncludesAPlanOnlyChange() {
        String changes = GenerationOrchestrationService.renderGenerationRepairChanges("same", "same", Map.of(), Map.of(), "{\"tests\":[{\"name\":\"old\"}]}",
                "{\"tests\":[{\"name\":\"new\"}]}");

        assertThat(changes).contains("--- test-plan.json", "- {\"tests\":[{\"name\":\"old\"}]}", "+ {\"tests\":[{\"name\":\"new\"}]}");
    }

    @Test
    void seededLayout_isOnTheFirstPromptOnly_andNotReplayedOnRetry() {
        when(workspace.probeWorkspaceLayout(any(), anyString())).thenReturn("--- ls -R solution template tests ---\nsolution:\nsrc");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("template unexpectedly passed all tests"),
                accepted());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
        }

        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        List<String> prompts = promptCaptor.getAllValues();
        assertThat(prompts.get(0)).as("attempt 1 carries the seeded layout").startsWith("=== INITIAL WORKSPACE").contains("Build a bubble sort exercise.");
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
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(4, Runnable.class).run();
            return accepted();
        });

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(verifier).verify(eq(sandbox), eq(SESSION_ID), eq(exercise), any(VerificationRequest.class), any(Runnable.class));
        verify(workspace).materializeRepositoryFiles(eq(sandbox), eq(SESSION_ID), any(), any(), any(), any(), any(), any(), any(), any());
        verify(sandbox, times(1)).createSession(any());
    }

    @Test
    void safetyStopAfterAuthoritativeVerify_preservesTheMechanicalVerdictAndSkipsCritic() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("");
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenAnswer(invocation -> {
            cancelled.set(true);
            return accepted();
        });

        try (GenerationOutcome outcome = generate(cancelled::get)) {
            assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
            assertThat(outcome.verification()).isEqualTo(accepted());
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().hasBlockingFindings()).isTrue();
        }
        verify(specFidelityCritic, never()).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void safetyStop_preservesChangedWorkspaceForReviewInsteadOfDiscardingIt() {
        when(exercise.getProblemStatement()).thenReturn("Original statement");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any()))
                .thenReturn(loopSession(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 3, "limit reached")));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("Improved statement");
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Library.java", "class Library {}"), false));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.ERROR);
            assertThat(outcome.hasCapturedArtifacts()).isTrue();
            assertThat(outcome.producedProblemStatement()).isEqualTo("Improved statement");
        }
    }

    static Stream<Arguments> nonRetryableVerifierFailures() {
        return Stream.of(
                Arguments.of("deterministic report rejection",
                        DifferentialVerificationService.VerificationInfrastructureException.reportRejected("invalid reports archive", new IOException("linked entry"))),
                Arguments.of("lost sandbox session", DifferentialVerificationService.VerificationInfrastructureException.sessionLost("sandbox deadline exceeded")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonRetryableVerifierFailures")
    void nonRetryableVerifierFailure_preservesTheCandidateWithoutRetryingOrCallingTheProviderAgain(String scenario, RuntimeException verifierFailure) {
        when(exercise.getProblemStatement()).thenReturn("Original statement");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("Improved statement");
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.SOLUTION), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Library.java", "class Library {}"), false));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenThrow(verifierFailure);

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.ERROR);
            assertThat(outcome.hasCapturedArtifacts()).isTrue();
            assertThat(outcome.producedFiles(RepositoryType.SOLUTION)).containsKey("src/Library.java");
        }

        verify(agentLoopRunner).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
    }

    @Test
    void transientVerifierInfrastructureFailureRetriesTheSameCandidateWithoutAnotherProviderCall() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class)))
                .thenThrow(new DifferentialVerificationService.VerificationInfrastructureException("temporary report transport failure", new IOException("transport")))
                .thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(agentLoopRunner).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(2)).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
    }

    @Test
    void budgetExhaustedLoop_stillRunsAuthoritativeVerificationInTheGenerationSession() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any()))
                .thenReturn(loopSession(new AgentLoopResult(AgentLoopResult.Status.BUDGET_EXHAUSTED, 100, "ran out of turns")));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("template passed a graded test"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.loopResult().status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
            assertThat(outcome.isMechanicallyVerified()).as("a rejected budget-exhausted run is not accepted").isFalse();
        }

        verify(verifier, atLeastOnce()).verify(eq(sandbox), eq(SESSION_ID), any(), any(VerificationRequest.class), any(Runnable.class));
    }

    @Test
    void acceptedOutcome_reusesVerificationExtractions_soPersistDoesNotReReadTheSandbox() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(workspace.extractRepository(sandbox, SESSION_ID, RepositoryType.TESTS, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("tests/T.java", "t ${testWorkingDirectory}"), false));
        when(workspace.extractRepository(sandbox, SESSION_ID, RepositoryType.TEMPLATE, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("template/M.java", "m"), false));
        when(workspace.extractRepository(sandbox, SESSION_ID, RepositoryType.SOLUTION, GenerationWorkspaceService.RepositorySeedMetadata.EMPTY))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("solution/S.java", "s"), false));
        when(workspace.extractProblemStatement(sandbox, SESSION_ID)).thenReturn("  # Title\n\nStatement  \n");

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
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
        ArgumentCaptor<Runnable> restoreCandidate = ArgumentCaptor.forClass(Runnable.class);
        verify(verifier).verify(eq(sandbox), eq(SESSION_ID), eq(exercise), request.capture(), restoreCandidate.capture());
        assertThat(request.getValue().producedProblemStatement()).isEqualTo("# Title\n\nStatement");

        restoreCandidate.getValue().run();
        InOrder resetThenMaterialize = inOrder(sandbox, workspace);
        resetThenMaterialize.verify(sandbox).resetSession(SESSION_ID);
        resetThenMaterialize.verify(workspace).materializeRepositoryFiles(eq(sandbox), eq(SESSION_ID), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // --- Deterministic advisory findings ---

    /**
     * Only the conjunction of two independent conditions downgrades a finding: the frozen contract must mandate a technique, and the finding's own prose must demand one. Each
     * row is a distinct path through that pair; the omitted fourth quadrant returns from the same provenance gate as the second row.
     */
    static Stream<Arguments> techniqueReclassificationCases() {
        // The critic states a weak-oracle finding as the surviving mutant's description, without "must".
        String techniqueProse = "an iterative implementation using an explicit stack";
        String behaviouralProse = "the recursive helper's base case is untested";
        String mandatingContract = "| R1 | `sum` must be implemented recursively |";
        String silentContract = "| R1 | negative salary invalid |";
        return Stream.of(
                // Both conditions hold: real but unrepairable, so it blocks publication without consuming an impossible repair round.
                Arguments.of("contract mandates a technique and the finding demands one", mandatingContract, techniqueProse, "it survives every assertion",
                        SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE, true),
                // Against a contract that mandates nothing, the same prose describes a behavioural gap the tests can be made to see, so the finding keeps its round.
                Arguments.of("contract mandates nothing", silentContract, techniqueProse, "it survives every assertion", SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, true),
                // On a recursion exercise nearly every finding says "recursive" somewhere; an untested base case is ordinary oracle work the downgrade must not swallow.
                Arguments.of("finding merely mentions the mandated technique", mandatingContract, behaviouralProse, "add a discriminator",
                        SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("techniqueReclassificationCases")
    void aFindingIsDowngradedOnlyWhenTheContractAndTheFindingBothDemandAnUngradeableTechnique(String scenario, String rulesBody, String findingRequirement, String findingDetail,
            SpecFidelityReport.Kind expectedKind, boolean expectedBlocking) {
        acceptedCandidateWithSpecAndTests(rulesBody);
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, findingRequirement, findingDetail))));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.specFidelityReport().findings()).singleElement().satisfies(finding -> {
                assertThat(finding.kind()).isEqualTo(expectedKind);
                assertThat(finding.isBlocking()).isEqualTo(expectedBlocking);
            });
        }
        // An ungradeable technique blocks publication but has no repair surface, so it still spends no impossible repair round.
        boolean schedulable = expectedBlocking && expectedKind != SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE;
        verify(agentLoopRunner, schedulable ? atLeast(2) : times(1)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void anUngradeableTechniqueRuleBlocksPublicationWithoutInvalidatingMechanicalVerification() {
        // Covers the wiring rather than the detector: the orchestrator must call it and merge its findings into the report.
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());
        SpecFidelityReport.Finding techniqueRule = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE, "must be recursive",
                "behavioural tests cannot see how a result was produced");
        when(specFidelityCritic.detectUnenforceableTechniqueRules(any())).thenReturn(List.of(techniqueRule));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).as("an ungradeable technique rule is a disclosure, never a rejection").isTrue();
            assertThat(outcome.specFidelityReport().findings()).anySatisfy(finding -> {
                assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE);
                assertThat(finding.isBlocking()).isTrue();
                assertThat(finding.requirement()).contains("must be recursive");
            });
        }
    }

    // --- Review availability ---

    @Test
    void aReviewThatCouldNotCompleteIsRetriedInsteadOfEndingTheRepairPhase() {
        // A review that returns no verdict reports "1 blocking quality gap" when it means "we could not review this"; the verdict fails open, but the repair work continues.
        acceptedCandidateWithSpecAndTests();
        SpecFidelityReport unavailable = SpecFidelityReport.qualityReviewUnavailable("the reviewer returned no verdict");
        SpecFidelityReport actionable = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "a wrong parser passes", "add a discriminating assertion")));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(unavailable, actionable, actionable);

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }
        verify(specFidelityCritic, atLeast(2)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(agentLoopRunner, atLeast(2)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void aSecondFailedReviewEndsTheRepairPhaseWithoutLoopingForever() {
        acceptedCandidateWithSpecAndTests();
        SpecFidelityReport unavailable = SpecFidelityReport.qualityReviewUnavailable("the reviewer returned no verdict");
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(unavailable);

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).as("an unreviewable candidate that passed every mechanical gate still stands").isTrue();
        }
        verify(specFidelityCritic, times(2)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(agentLoopRunner, times(1)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    // --- Contract witnesses ---

    private static final ContractWitness WITNESS = new ContractWitness("R1", "testWitnessNegativeSalary",
            "@Test\nvoid testWitnessNegativeSalary() { assertEquals(0, parse(\"a|b|-5\"), \"negative is invalid\"); }", "accepts negative salary records");

    private static SpecFidelityCriticService.ReferenceWitnessReview approvedForAdoption(ContractWitness witness) {
        return new SpecFidelityCriticService.ReferenceWitnessReview(List.of(GenerationReviewSupport.approvedContractWitnessAvailable(witness)), List.of(), List.of(witness),
                List.of(), List.of(), List.of());
    }

    /** An accepted candidate whose workspace holds a SPEC and one graded test, the state the witness pass needs to run at all. */
    private void acceptedCandidateWithSpecAndTests() {
        acceptedCandidateWithSpecAndTests("| R1 | negative salary invalid |");
    }

    private void acceptedCandidateWithSpecAndTests(String rulesBody) {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.TESTS), any()))
                .thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of("test/RosterParserTest.java", "package p;\nclass RosterParserTest { }"), false));
        when(sandbox.exec(eq(SESSION_ID), any(), eq("cat"), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(3);
            return path.endsWith("/SPEC.md") ? new SandboxExecResultDTO(0, "## Rules\n" + rulesBody, "", false) : new SandboxExecResultDTO(1, "", "not found", false);
        });
    }

    @Test
    void validatedContractWitness_reachesTheReportWithoutFlippingTheVerdict() {
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(WITNESS));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new ContractWitnessOutcome(WITNESS, ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED, "")));
        when(specFidelityCritic.adjudicateReferenceWitnesses(anyString(), anyString(), any(), any(), any())).thenReturn(approvedForAdoption(WITNESS));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).as("an advisory witness never unseats an accepted candidate").isTrue();
            assertThat(outcome.specFidelityReport().findings()).anySatisfy(finding -> {
                assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE);
                assertThat(finding.isBlocking()).as("a validated witness shows the test is legal, not that coverage is missing").isFalse();
                assertThat(finding.detail()).contains("testWitnessNegativeSalary");
            });
        }
    }

    @Test
    void contractWitnessThatTheReferenceSolutionDoesNotSatisfy_neverReachesTheReport() {
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(WITNESS));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), any(), any(), any(), any())).thenReturn(List.of());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.specFidelityReport().findings()).noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE);
        }
    }

    @Test
    void witnessesProbeReferenceCorrectnessWhileSomethingElseStillBlocks() {
        // Optional adoption waits, but reference-correctness probing must not: another blocker cannot be allowed to hide a broken reference implementation.
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "rollback", "a plausible wrong implementation survives"))));

        try (GenerationOutcome ignored = generate(() -> false)) {
            verify(specFidelityCritic, atLeastOnce()).authorContractWitnesses(anyString(), anyString(), anyString(), any(), any());
        }
    }

    @Test
    void theWitnessAdoptionPromptOffersTheTestsInsteadOfReportingBlockers() {
        // The times(2) pins the budget: without a round the agent never reads the witness, and more than one would let witnesses drive repeated rewrites of a finished exercise.
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(WITNESS));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new ContractWitnessOutcome(WITNESS, ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED, "")));
        when(specFidelityCritic.adjudicateReferenceWitnesses(anyString(), anyString(), any(), any(), any())).thenReturn(approvedForAdoption(WITNESS));

        try (GenerationOutcome ignored = generate(() -> false)) {
            ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
            verify(agentLoopRunner, times(2)).runSession(anyString(), any(), prompts.capture(), any(), anyInt(), any(), any(), any());
            assertThat(prompts.getAllValues().getLast()).contains("fully verified and accepted", "unless an existing assertion already distinguishes")
                    .doesNotContain("review blockers");
        }
    }

    @Test
    void aWitnessNeverDisplacesABlockingRepair() {
        // Blocking findings are defects; a witness is an offer. If both are present the defect must be scheduled first.
        acceptedCandidateWithSpecAndTests();
        SpecFidelityReport blocking = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE, "rollback", "a plausible wrong implementation survives")));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(blocking);
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(WITNESS));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new ContractWitnessOutcome(WITNESS, ContractWitnessOutcome.Disposition.REFERENCE_PASSED_STARTER_FAILED, "")));

        try (GenerationOutcome ignored = generate(() -> false)) {
            ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
            verify(agentLoopRunner, atLeast(2)).runSession(anyString(), any(), prompts.capture(), any(), anyInt(), any(), any(), any());
            assertThat(prompts.getAllValues().get(1)).as("the first repair addresses the blocker, not the witness").contains("review blockers");
        }
    }

    // --- Termination reason: every exit of the attempt loop names itself ---

    /** The semantic repair budget also derives the attempt cap ({@link GenerationAttemptLoop#MAX_MECHANICAL_ATTEMPTS} + budget + 1). */
    private GenerationOrchestrationService serviceWithRepairBudget(int maxSemanticRepairs) {
        return new GenerationOrchestrationService(Optional.of(sandbox), workspace, agentLoopRunner, verifier, systemPromptService, structuralOracleSeeder, specFidelityCritic,
                jobService, Optional.of(testCaseRepository), 100, maxSemanticRepairs, stagedGenerationRunner, false, stageCheckService, new AgentTranscriptWriter(""),
                new de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry());
    }

    @Test
    void aRepairBudgetOutsideTheReviewedRange_isRejectedRatherThanSilentlyDefaulted() {
        // Substituting the default for an out-of-range value makes the setting look applied while the run quietly stops repairing earlier than the deployment was tuned for.
        assertThatThrownBy(() -> serviceWithRepairBudget(0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("artemis.hyperion.agent.max-semantic-repairs");
        assertThatThrownBy(() -> serviceWithRepairBudget(HyperionGenerationConfigurationValidator.MAX_SEMANTIC_REPAIRS + 1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("artemis.hyperion.agent.max-semantic-repairs");
    }

    @Test
    void acceptedWithNothingBlocking_terminatesAsConverged() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.CONVERGED);
        }
    }

    @Test
    void repairBudgetSpentWithBlockersRemaining_terminatesAsRepairBudgetExhausted() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(reportWith("unbounded blocker", "secondary blocker"), reportWith("unbounded blocker"));

        try (GenerationOutcome outcome = serviceWithRepairBudget(1).generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, () -> false, null,
                null, null)) {
            assertThat(outcome.isMechanicallyVerified()).as("the exercise still stands; only the repair budget ran out").isTrue();
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.REPAIR_BUDGET_EXHAUSTED);
        }
    }

    @Test
    void lastAttemptWithBlockersRemaining_terminatesAsAttemptCapReached() {
        // Budget 1 caps attempts at MAX_MECHANICAL_ATTEMPTS + 1 + 1 = 6. Three pre-repair rejections, one repair, one post-repair correction, and the sixth attempt is the last.
        makeSolutionChangeOnEachExtraction();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("first"), rejected("second"), rejected("third"),
                accepted(), rejected("the repair broke the build"), accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(reportWith("unbounded blocker", "secondary blocker"), reportWith("unbounded blocker"));

        try (GenerationOutcome outcome = serviceWithRepairBudget(1).generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, () -> false, null,
                null, null)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.ATTEMPT_CAP_REACHED);
        }

        verify(agentLoopRunner, times(6)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void mechanicalPhaseSpentBeforeAnyRepair_terminatesAsMechanicalRepairExhausted() {
        makeSolutionChangeOnEachExtraction();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("still does not build"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.MECHANICAL_REPAIR_EXHAUSTED);
        }

        verify(agentLoopRunner, times(MAX_MECHANICAL_ATTEMPTS)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void repairThatKeepsBreakingTheBuild_terminatesAsPostRepairCorrectionExhausted() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted(), rejected("repair no longer compiles"),
                rejected("repair still does not compile"));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("invalid events"));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Verified", "# Broken repair", "# Still broken repair");

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).as("the preserved checkpoint is the verified one, not the broken repair").isTrue();
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.POST_REPAIR_CORRECTION_EXHAUSTED);
        }
    }

    @Test
    void reviewerThatNeverReturnsAVerdict_terminatesAsReviewUnavailable() {
        // Distinct from an exhausted budget: the rounds were never spent, because the instrument that names the work failed.
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(SpecFidelityReport.qualityReviewUnavailable("the reviewer returned no verdict"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
        }
    }

    @Test
    void unavailableReviewAfterRepairRetainsTheReviewedPredecessor() {
        acceptedCandidateWithSpecAndTests();
        SpecFidelityReport reviewed = reportWith("boundary behavior is untested");
        SpecFidelityReport unavailable = SpecFidelityReport.qualityReviewUnavailable("the reviewer returned no verdict");
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reviewed, unavailable, unavailable);
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Reviewed predecessor", "# Unreviewed repair");

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
            assertThat(outcome.producedProblemStatement()).isEqualTo("# Reviewed predecessor");
            assertThat(outcome.specFidelityReport()).isEqualTo(reviewed);
        }
    }

    @Test
    void unchangedResubmittedCandidate_terminatesAsUnchangedCandidateResubmitted() {
        makeAllExtractionsEmpty();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("template passed every test"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.UNCHANGED_CANDIDATE_RESUBMITTED);
        }

        verify(verifier, times(1)).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
    }

    @Test
    void cancellationInsideTheLoop_terminatesAsCancelled() {
        SpecFidelityReport contractBlocker = reportWith("invalid events");
        AgentLoopResult cancelledRepair = new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 2, "cancelled");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()), loopSession(cancelledRepair));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(contractBlocker);

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.CANCELLED);
        }
    }

    @Test
    void agentLoopError_terminatesAsAgentError() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any()))
                .thenReturn(loopSession(new AgentLoopResult(AgentLoopResult.Status.ERROR, 2, "provider stopped responding")));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.AGENT_ERROR);
        }
    }

    @Test
    void buildEnvironmentFailure_terminatesAsEnvironmentUnavailable() {
        when(verifier.checkBuildEnvironment(sandbox, SESSION_ID, exercise)).thenReturn(Optional.of("The sandbox image is not offline-ready."));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.ENVIRONMENT_UNAVAILABLE);
        }
    }

    @Test
    void unexpectedFailureWhileRepairing_terminatesAsRunFailed() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("invalid events"));
        when(structuralOracleSeeder.seedIfStructuralDiff(any(), anyString(), any())).thenReturn(SeededStructuralTests.EMPTY)
                .thenThrow(new IllegalStateException("repair extraction failed"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.RUN_FAILED);
        }
    }

    @Test
    void cancellationBeforeTheSandboxExists_terminatesAsCancelled() {
        try (GenerationOutcome outcome = generate(() -> true)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.CANCELLED);
        }
    }

    // --- Per-round finding drain: the counts that tell a recurring finding apart from a fresh one of the same category ---

    /** Captures both halves of the progress channel: the human-readable lines and the structured round telemetry that rides on the same events. */
    private static final class RecordingProgressSink implements GenerationProgressSink {

        private final List<String> lines = new ArrayList<>();

        private final List<ExerciseGenerationRepairRoundDTO> rounds = new ArrayList<>();

        @Override
        public void accept(String message) {
            lines.add(message);
        }

        @Override
        public void progress(String message, ExerciseGenerationRepairRoundDTO round) {
            lines.add(message);
            rounds.add(round);
        }
    }

    private RecordingProgressSink generateRecordingProgress() {
        RecordingProgressSink sink = new RecordingProgressSink();
        try (GenerationOutcome ignored = service.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, () -> false, sink, null, null)) {
            return sink;
        }
    }

    private void acceptedCandidateReviewedAs(SpecFidelityReport first, SpecFidelityReport... rest) {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(first, rest);
    }

    @Test
    void theSameFindingAfterARepair_isCountedAsCarriedOverRatherThanFresh() {
        acceptedCandidateReviewedAs(reportWith("emoji graphemes"), reportWith("emoji graphemes"));

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds).hasSize(2);
        assertThat(rounds.get(0)).as("the first round has nothing to compare against").satisfies(round -> {
            assertThat(round.round()).isEqualTo(1);
            assertThat(round.attempt()).isEqualTo(1);
            assertThat(round.blocking()).isEqualTo(1);
            assertThat(round.carriedOver()).isZero();
            assertThat(round.drained()).isZero();
            assertThat(round.fresh()).isEqualTo(1);
        });
        assertThat(rounds.get(1)).as("the repair did not fix the named defect").satisfies(round -> {
            assertThat(round.carriedOver()).isEqualTo(1);
            assertThat(round.drained()).isZero();
            assertThat(round.fresh()).isZero();
        });
    }

    @Test
    void aDifferentFindingOfTheSameKind_isCountedAsDrainedPlusFreshRatherThanCarriedOver() {
        // Both rounds carry exactly one UNCOVERED_REQUIREMENT, so a Kind histogram cannot tell "unrepaired" from "bottomless well".
        acceptedCandidateReviewedAs(reportWith("emoji graphemes"), reportWith("CJK graphemes"), SpecFidelityReport.empty());

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds).hasSizeGreaterThanOrEqualTo(2);
        assertThat(rounds.get(1)).satisfies(round -> {
            assertThat(round.blocking()).as("the category histogram is identical to the previous round").isEqualTo(1);
            assertThat(round.carriedOver()).isZero();
            assertThat(round.drained()).isEqualTo(1);
            assertThat(round.fresh()).isEqualTo(1);
        });
    }

    @Test
    void aRequirementWhoseTextMerelyStartsWithALetterAndDigits_keepsItsOwnIdentity() {
        acceptedCandidateReviewedAs(reportWith("r2d2 identifiers are rejected"), reportWith("identifiers are rejected"), SpecFidelityReport.empty());

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds.get(1).carriedOver()).isZero();
        assertThat(rounds.get(1).fresh()).isEqualTo(1);
    }

    @Test
    void aMechanicallyRejectedAttempt_isNotAReviewRoundAndDoesNotResetTheBaseline() {
        // A rejected attempt empties the report without asking the reviewer anything; counting it as a round would report every finding drained when nothing was reviewed.
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted(), rejected("the repair broke the build"),
                accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("emoji graphemes", "CJK graphemes"),
                reportWith("emoji graphemes"), SpecFidelityReport.empty());
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Verified", "# Broken repair", "# Corrected repair", "# Final");

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds).extracting(ExerciseGenerationRepairRoundDTO::attempt).as("only the attempts that were actually reviewed produce a round").containsExactly(1, 3, 4);
        assertThat(rounds.get(1)).satisfies(round -> {
            assertThat(round.round()).isEqualTo(2);
            assertThat(round.carriedOver()).as("the baseline survived the unreviewed attempt in between").isEqualTo(1);
            assertThat(round.drained()).isEqualTo(1);
        });
    }

    @Test
    void theRoundLine_readsAsProgressAndCarriesItsCountsOnTheSameEvent() {
        acceptedCandidateReviewedAs(reportWith("emoji graphemes", "CJK graphemes"), reportWith("CJK graphemes"), SpecFidelityReport.empty());

        RecordingProgressSink sink = generateRecordingProgress();

        assertThat(sink.lines).as("the existing human-readable lines are kept; the telemetry is additive")
                .anySatisfy(line -> assertThat(line).contains("The review found", "1 blocking"));
        assertThat(sink.lines).anySatisfy(line -> assertThat(line).contains("Quality review round 2", "1 still open from the previous round", "1 resolved", "0 new"));
        assertThat(sink.lines).anySatisfy(line -> assertThat(line).contains("Quality review round 3", "no issues remain"));
    }

    @Test
    void aFailingContractWitnessAuthoringPassCostsTheExerciseNothing() {
        // The model proposal is advisory scaffolding on an already-accepted candidate; an unavailable proposal must not disturb the verdict.
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenThrow(new IllegalStateException("provider down"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().findings()).noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE);
        }
    }

    @Test
    void aContractWitnessProbeInfrastructureFailurePreservesTheVerifiedPreReviewCheckpoint() {
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(WITNESS));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), any(), any(), any(), any()))
                .thenThrow(new DifferentialVerificationService.VerificationInfrastructureException("restore failed", new IllegalStateException("session lost")));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.RUN_FAILED);
            assertThat(outcome.specFidelityReport().findings()).singleElement()
                    .satisfies(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE));
        }
    }

    @Test
    void effortProfile_replacesTheEngineWithProfilePinnedCollaboratorsAndItsOwnTurnBudget() {
        when(agentLoopRunner.forSettings(any())).thenReturn(agentLoopRunner);
        when(specFidelityCritic.forSettings(any())).thenReturn(specFidelityCritic);
        when(stagedGenerationRunner.forSettings(any(), any(), any())).thenReturn(stagedGenerationRunner);
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        HyperionGenerationSettings draft = new HyperionGenerationSettings("draft", "Quick draft", 20, java.time.Duration.ofMinutes(12), 600_000L, false, "CONTINUOUS", 64_000, null,
                false, false);

        try (GenerationOutcome ignored = newService().generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, () -> false, null, null, null,
                null, draft)) {
            verify(agentLoopRunner).forSettings(draft);
            verify(specFidelityCritic).forSettings(draft);
            verify(stagedGenerationRunner).forSettings(eq(draft), any(), any());
            // 20 from the profile, not the 100 this service was constructed with.
            verify(agentLoopRunner).runSession(anyString(), any(), anyString(), any(), eq(20), any(), any(), any());
        }
    }

    @Test
    void withoutAnEffortProfile_theEngineIsNotDerivedAtAll() {
        // A deployment that configures no profiles keeps running on the shared singletons.
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));

        try (GenerationOutcome ignored = generate(() -> false)) {
            verify(agentLoopRunner, never()).forSettings(any());
            verify(specFidelityCritic, never()).forSettings(any());
            verify(stagedGenerationRunner, never()).forSettings(any(), any(), any());
        }
    }

    // === Sandbox teardown versus artifact capture (job a1f1d61a) ===

    /** Records the node-local cancel hook the run registers, so a test can dispatch it the way {@code GenerationJobService} does: on another pool, mid-run. */
    private AtomicReference<Runnable> captureCancelHook() {
        AtomicReference<Runnable> hook = new AtomicReference<>();
        doAnswer(invocation -> {
            hook.set(invocation.getArgument(1));
            return null;
        }).when(jobService).registerCancelHook(eq(JOB_ID), any());
        return hook;
    }

    /**
     * Runs the cancel hook the way the production dispatch does — on {@code GenerationJobService}'s cancellation executor rather than the generation thread — and asserts that it
     * came back. A hook that blocked here would stall every unrelated cancellation queued behind it on that shared executor.
     */
    private static void dispatchOnCancellationExecutor(Runnable cancelHook) {
        Thread cancellationExecutor = new Thread(cancelHook, "cancellation-executor");
        cancellationExecutor.start();
        try {
            cancellationExecutor.join(TimeUnit.SECONDS.toMillis(5));
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(cancellationExecutor.isAlive()).as("the cancel hook must not block on the capture that is in flight").isFalse();
    }

    /**
     * The incident: the provider threw during authoring, {@code markUncertain} requested a system cancellation, and the cancel hook destroyed the session while the diagnostic
     * capture was copying the repositories out — so all three copy-outs failed and the run retained nothing.
     */
    @Test
    void aCancellationArrivingWhileTheWorkIsCopiedOut_isDeferredAndEveryCopyOutStillSucceeds() {
        AtomicReference<Runnable> cancelHook = captureCancelHook();
        AtomicBoolean cancelled = new AtomicBoolean();
        List<String> sandboxEvents = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean sessionDestroyed = new AtomicBoolean();
        doAnswer(invocation -> {
            sandboxEvents.add("destroy");
            sessionDestroyed.set(true);
            return null;
        }).when(sandbox).destroySession(SESSION_ID);
        when(workspace.extractRepository(any(), anyString(), any(), any())).thenAnswer(invocation -> {
            RepositoryType type = invocation.getArgument(2);
            sandboxEvents.add("copyOut:" + type.name());
            if (sessionDestroyed.get()) {
                throw new IllegalStateException("Remote sandbox operation COPY_OUT failed: session " + SESSION_ID + " is not active on this build agent");
            }
            if (type == RepositoryType.SOLUTION) {
                // The observed timing: the destroy is queued about a second before the capture path runs, and lands while it is mid-copy.
                dispatchOnCancellationExecutor(cancelHook.get());
            }
            return new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Attempt.java", "candidate from " + type), false);
        });
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            cancelled.set(true);
            throw new IllegalStateException("OpenAIIoException while streaming the provider response");
        });

        try (GenerationOutcome outcome = service.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, cancelled::get, null, null, null)) {
            assertThat(outcome.capturedProducedFiles()).as("the work the failed run produced is retained rather than lost with the session").containsKeys(RepositoryType.SOLUTION,
                    RepositoryType.TEMPLATE, RepositoryType.TESTS);
        }

        assertThat(sandboxEvents).containsSubsequence("copyOut:SOLUTION", "copyOut:TEMPLATE", "copyOut:TESTS", "destroy");
        assertThat(sandboxEvents).last().as("the session is torn down only once its work has been copied out").isEqualTo("destroy");
        verify(sandbox, times(1)).destroySession(SESSION_ID);
    }

    /** Once the session really is gone, the capture path must skip its copy-outs rather than issue calls that can only fail and log "Could not extract … files". */
    @Test
    void aCaptureStartingAfterTheSessionWasDestroyed_issuesNoCopyOutsAtAll() {
        AtomicReference<Runnable> cancelHook = captureCancelHook();
        AtomicBoolean cancelled = new AtomicBoolean();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            cancelled.set(true);
            dispatchOnCancellationExecutor(cancelHook.get());
            throw new IllegalStateException("OpenAIIoException while streaming the provider response");
        });

        try (GenerationOutcome outcome = service.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, cancelled::get, null, null, null)) {
            assertThat(outcome.capturedProducedFiles()).isEmpty();
        }

        verify(workspace, never()).extractRepository(any(), anyString(), any(), any());
        verify(workspace, never()).cleanTransientBuildOutputs(any(), anyString());
        verify(workspace, never()).extractProblemStatement(any(), anyString());
        verify(sandbox, times(1)).destroySession(SESSION_ID);
    }

    /** A run that dies during the artifacts stage keeps what that stage authored, because the stage boundary snapshotted it while the sandbox was still alive. */
    @Test
    void aProviderFailureAfterAnAuthoringStageBoundary_retainsThatStagesSnapshot() {
        GenerationOrchestrationService stagedService = newService(true);
        AtomicReference<Runnable> cancelHook = captureCancelHook();
        AtomicBoolean cancelled = new AtomicBoolean();
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(),
                any())).thenAnswer(invocation -> {
                    Consumer<GenerationStage> stageBoundarySink = invocation.getArgument(15);
                    stageBoundarySink.accept(GenerationStage.TESTS);
                    // markUncertain -> requestSystemCancellation -> the hook is dispatched on the cancellation executor, and the session dies.
                    cancelled.set(true);
                    dispatchOnCancellationExecutor(cancelHook.get());
                    throw new IllegalStateException("OpenAIIoException while streaming the provider response");
                });

        try (GenerationOutcome outcome = stagedService.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, cancelled::get, null, null,
                null)) {
            assertThat(outcome.capturedProducedFiles()).as("the snapshot taken at the end of the artifacts stage survives the dead session").containsKey(RepositoryType.SOLUTION);
            assertThat(outcome.capturedProducedFiles().get(RepositoryType.SOLUTION)).containsKey("src/Attempt.java");
        }

        // Three: the stage boundary's own copy-out. The dead session is never asked again.
        verify(workspace, times(3)).extractRepository(any(), anyString(), any(), any());
    }

    /** Without a completed stage there is nothing to fall back to, and the run must still not issue copy-outs against a destroyed session. */
    @Test
    void aProviderFailureBeforeAnyStageBoundary_retainsNothingAndStillIssuesNoCopyOuts() {
        GenerationOrchestrationService stagedService = newService(true);
        AtomicReference<Runnable> cancelHook = captureCancelHook();
        AtomicBoolean cancelled = new AtomicBoolean();
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(),
                any())).thenAnswer(invocation -> {
                    cancelled.set(true);
                    dispatchOnCancellationExecutor(cancelHook.get());
                    throw new IllegalStateException("OpenAIIoException while streaming the provider response");
                });

        try (GenerationOutcome outcome = stagedService.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, cancelled::get, null, null,
                null)) {
            assertThat(outcome.capturedProducedFiles()).isEmpty();
        }

        verify(workspace, never()).extractRepository(any(), anyString(), any(), any());
    }
}
