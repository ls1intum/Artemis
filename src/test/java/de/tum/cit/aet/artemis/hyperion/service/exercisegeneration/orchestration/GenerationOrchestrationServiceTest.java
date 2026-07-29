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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
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
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
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

    /** Referenced, not copied: a change to the production budget must fail at the arithmetic below rather than pass against a stale literal. */
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
        when(workspace.extractRepository(any(), anyString(), any(), any())).thenReturn(new GenerationWorkspaceService.RepositoryExtraction(Map.of(), false));
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("PROBLEM STATEMENT");
        when(workspace.seedWorkspace(any(), anyString(), any(), any(), anyBoolean())).thenReturn(new GenerationWorkspaceService.WorkspaceSeed(Map.of(), Map.of()));
        // Mirror the real predicate's non-trivial threshold so fixtures with instructor statements keep steering the brief (the mock would otherwise return false for all).
        when(systemPromptService.isAuthoritativeProblemStatement(any())).thenAnswer(invocation -> {
            String statement = ((ProgrammingExercise) invocation.getArgument(0)).getProblemStatement();
            return statement != null && statement.strip().length() >= 40;
        });
        when(verifier.checkBuildEnvironment(any(), anyString(), any())).thenReturn(Optional.empty());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());
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
        // Staged generation disabled by default so the existing battery of tests below keeps exercising the original single-agent-loop-call path unmodified; a dedicated test
        // below constructs the flag=true variant to prove the delegation seam itself.
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

    /**
     * Seeds a non-empty TEMPLATE baseline. Without one, "the repository could not be read" and "the repository legitimately produced nothing" are the same empty map, so the
     * extraction-failed guard is unobservable: a diagnostic capture may only report repositories it actually read.
     */
    private void seedNonEmptyTemplateBaseline() {
        when(workspace.seedWorkspace(any(), anyString(), any(), any(), anyBoolean())).thenReturn(
                new GenerationWorkspaceService.WorkspaceSeed(Map.of(), Map.of(), Map.of(), Map.of(RepositoryType.TEMPLATE, Map.of("src/Given.java", "class Given {}"))));
    }

    private void makeSolutionChangeOnEachExtraction() {
        AtomicInteger extraction = new AtomicInteger();
        when(workspace.extractRepository(any(), anyString(), eq(RepositoryType.SOLUTION), any()))
                .thenAnswer(invocation -> new GenerationWorkspaceService.RepositoryExtraction(Map.of("src/Attempt.java", "attempt " + extraction.incrementAndGet()), false));
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
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new StagedGenerationRunner.StagedRunOutcome(completed(), null));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = stagedService.generate(exercise, user, "Build a bubble sort exercise.", JOB_ID, GenerationMode.GENERATE, () -> false, null, null, null)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(stagedGenerationRunner, times(1)).run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), any());
        verify(agentLoopRunner, never()).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void sourceBrief_runsSpecStageWithoutLettingAnIntermediateDraftAnchorTheSpecification() {
        GenerationOrchestrationService stagedService = newService(true);
        String draft = "# Draft playlist exercise\n\nThis generated draft is long enough to look authoritative but may have omitted explicit requirements.";
        String sourceBrief = "Teach Strategy with three playlist strategies. Students must create the interface. Include a UML diagram.";
        when(exercise.getProblemStatement()).thenReturn(draft);
        when(stagedGenerationRunner.run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new StagedGenerationRunner.StagedRunOutcome(completed(), null));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> rawBrief = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> specStageApplies = ArgumentCaptor.forClass(Boolean.class);

        try (GenerationOutcome outcome = stagedService.generate(exercise, user, "resolved instruction", JOB_ID, GenerationMode.GENERATE, () -> false, null, null, null,
                sourceBrief)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(stagedGenerationRunner).run(any(), any(), any(), prompt.capture(), rawBrief.capture(), any(), any(), anyString(), any(), any(), any(), any(),
                specStageApplies.capture(), any());
        assertThat(specStageApplies.getValue()).isTrue();
        assertThat(prompt.getValue()).contains("PRIMARY SOURCE REQUIREMENTS", sourceBrief).doesNotContain("CURRENT AI-GENERATED DRAFT", draft);
        assertThat(rawBrief.getValue()).isEqualTo(sourceBrief);
        verify(workspace).seedWorkspace(any(), anyString(), eq(exercise), eq(GenerationMode.GENERATE), eq(false));
    }

    @Test
    void stagedGenerationDisabled_generateJava_usesTheSingleAgentLoopCallDirectly() {
        // service (built by newService()) has staged generation disabled, matching the default in every other test in this file: flag off must be a no-op, leaving the
        // original single, open-ended agent-loop call as the only path — the seam introduced for staged generation must not change existing behaviour.
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(agentLoopRunner, times(1)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        // The 14-argument overload is the one the attempt loop actually calls; verifying the 13-argument convenience overload would pass even with the flag on.
        verify(stagedGenerationRunner, never()).run(any(), any(), any(), anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyBoolean(), any());
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
        when(structuralOracleSeeder.seedIfStructuralDiff(any(), anyString(), any())).thenReturn(Set.of(), Set.of("StructuralTest"));
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
        // Ordering is incidental; the property is the data dependency. The verifier requires every seeded grading check to appear in the student checklist, and it can only
        // enforce that against the names this run actually seeded — passing an empty set silently disables the gate while every ordering assertion still holds.
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(structuralOracleSeeder.seedIfStructuralDiff(eq(sandbox), eq(SESSION_ID), eq(exercise))).thenReturn(Set.of("testMethods[Strategy]", "testClass[Strategy]"));
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

    private static SpecFidelityReport reportWith(String requirement) {
        return new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, requirement, "no test covers it")));
    }

    private static SpecFidelityReport advisoryReportWith(String requirement) {
        return new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, requirement, "an example would improve clarity")));
    }

    @ParameterizedTest
    @EnumSource(SpecFidelityReport.Kind.class)
    void mechanicalAcceptanceIsIndependentFromQualityReviewDisposition(SpecFidelityReport.Kind kind) {
        SpecFidelityReport report = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(kind, "requirement", "detail")));

        GenerationOutcome outcome = new GenerationOutcome(completed(), accepted(), null, null, null, Map.of(), "", report, Map.of());

        assertThat(outcome.isMechanicallyVerified()).as("quality finding %s must never change the mechanical verdict", kind).isTrue();
    }

    @Test
    void acceptedWithAdvisoryPresentationFinding_doesNotSpendRetryOrFlipVerdict() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(advisoryReportWith("state rollback"));

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

        verify(specFidelityCritic, times(2)).critiqueAdaptation(contains("RUN INSTRUCTION (authoritative adaptation request):\nChange one method only"), eq("PROBLEM STATEMENT"),
                any(), eq(""), any(), any(), any(), any());
        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(specFidelityCritic, never()).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
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
        // The critic returning clean on evidence it could only partly see is exactly the case a "no findings" report must not be trusted for: without the truncation merge the
        // run reports a fully reviewed adaptation while lines it never saw ship unexamined.
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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(SpecFidelityReport.empty());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
        }

        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), promptCaptor.capture(), any(), anyInt(), any(), any(), any());
        String retryPrompt = promptCaptor.getAllValues().get(1);
        assertThat(retryPrompt).as("the retry prompt still carries the hard rejection").contains("rejected by the differential verifier").contains("template passed a test");
        assertThat(retryPrompt).doesNotContain("Exercise-quality issues");
        verify(specFidelityCritic).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void lastMechanicalAttemptStillLeavesRoomForOneSemanticRepair() {
        makeSolutionChangeOnEachExtraction();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(rejected("first mechanical defect"),
                rejected("second mechanical defect"), rejected("third mechanical defect"), accepted(), accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("generic theme"), SpecFidelityReport.empty());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().hasBlockingFindings()).isFalse();
        }

        verify(agentLoopRunner, times(5)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
        verify(verifier, times(5)).verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class));
        verify(specFidelityCritic, times(2)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void acceptedCandidateWithContractBlockerRetriesAndAcceptsTheRepair() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("emoji"), SpecFidelityReport.empty());

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
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "delegation", "does not prove forwarding"),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, "starter docs", "missing point-of-use documentation")));
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(mixedReview, SpecFidelityReport.empty());

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome ignored = generate(() -> false)) {
        }

        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), prompts.capture(), any(), anyInt(), any(), any(), any());
        assertThat(prompts.getAllValues().get(1)).contains("null inputs").doesNotContain("does not prove forwarding", "missing point-of-use documentation");
    }

    @Test
    void aNewGroundedFindingAfterRepairGetsOneMoreBoundedSemanticRepair() {
        SpecFidelityReport originalReview = reportWith("original contract blocker");
        SpecFidelityReport repairedReview = reportWith("repair introduced a different blocker");
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(originalReview, repairedReview,
                SpecFidelityReport.empty());
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Original verified candidate", "# First repair", "# Complete repair");

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.producedProblemStatement()).isEqualTo("# Complete repair");
            assertThat(outcome.specFidelityReport()).isEqualTo(SpecFidelityReport.empty());
        }

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(agentLoopRunner, times(3)).runSession(anyString(), any(), prompts.capture(), any(), anyInt(), any(), any(), any());
        assertThat(prompts.getAllValues().get(2)).contains("repair introduced a different blocker").doesNotContain("original contract blocker");
        verify(specFidelityCritic, times(3)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void semanticReviewContinuitySurvivesTheAllowedMechanicalCorrection() {
        SpecFidelityReport originalReview = reportWith("the transition oracle is too weak");
        makeSolutionChangeOnEachExtraction();
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted(), rejected("repair no longer compiles"),
                accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(originalReview, SpecFidelityReport.empty());
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Original", "# Broken repair", "# Corrected repair");

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().hasBlockingFindings()).isFalse();
        }

        ArgumentCaptor<SpecFidelityReport> previousReview = ArgumentCaptor.forClass(SpecFidelityReport.class);
        ArgumentCaptor<String> repairDelta = ArgumentCaptor.forClass(String.class);
        verify(specFidelityCritic, times(2)).critique(any(), any(), any(), any(), any(), any(), previousReview.capture(), any(), repairDelta.capture(), any());
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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("invalid durations"),
                SpecFidelityReport.empty());

        ArgumentCaptor<String> reviewBrief = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> agentPrompt = ArgumentCaptor.forClass(String.class);
        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }

        verify(specFidelityCritic, times(2)).critique(reviewBrief.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(contractBlocker);
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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(contractBlocker);
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Mechanically verified candidate");
        when(structuralOracleSeeder.seedIfStructuralDiff(any(), anyString(), any())).thenReturn(Set.of()).thenThrow(new IllegalStateException("repair extraction failed"));

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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(contractBlocker);
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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(originalReview);
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Reviewed candidate", "# Unreviewed repair");

        try (GenerationOutcome outcome = generate(cancelled::get)) {
            assertThat(outcome.producedProblemStatement()).isEqualTo("# Reviewed candidate");
            assertThat(outcome.specFidelityReport()).isEqualTo(originalReview);
        }

        verify(specFidelityCritic).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void criticThrows_runFailsClosedWithoutRetryingAnUnchangedCandidate() {
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new RuntimeException("critic exploded"));

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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(partialReview, SpecFidelityReport.empty());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }
        verify(agentLoopRunner, times(2)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void reviewProgressDistinguishesBlockingFindingsFromAdvisories() {
        SpecFidelityReport mixedReview = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "boundary", "no boundary assertion"),
                        new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, "workflow", "an example would help")));
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(mixedReview);
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

        verify(specFidelityCritic).critique(anyString(), anyString(), namesCaptor.capture(), artifactsCaptor.capture(), any(), any(), any(), any(), any(),
                testPlanCaptor.capture());
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
        verify(specFidelityCritic, never()).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
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
     * The reclassification is guarded by two independent conditions — the frozen contract must mandate a technique, and the finding's own prose must demand one — so only the
     * conjunction downgrades. Each row below is a distinct path through that pair. A fourth quadrant (contract silent, prose behavioural) is omitted: it returns from the same
     * provenance gate as the second row, so it could only restate it.
     */
    static Stream<Arguments> techniqueReclassificationCases() {
        // Phrased the way the critic actually phrases a weak-oracle finding: the requirement carries the surviving mutant's description, in the critic's voice, without "must".
        String techniqueProse = "an iterative implementation using an explicit stack";
        String behaviouralProse = "the recursive helper's base case is untested";
        String mandatingContract = "| R1 | `sum` must be implemented recursively |";
        String silentContract = "| R1 | negative salary invalid |";
        return Stream.of(
                // Both conditions hold: the finding is real but unrepairable, so it is downgraded to an advisory disclosure and must not hold a repair round.
                Arguments.of("contract mandates a technique and the finding demands one", mandatingContract, techniqueProse, "it survives every assertion",
                        SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE, false),
                // Provenance is what keeps the downgrade honest: against a contract that mandates nothing the same prose describes a behavioural gap the tests can be made to
                // see, so it keeps its round. Without this gate a misread finding would cost a round on every exercise, not only the ones carrying the defect.
                Arguments.of("contract mandates nothing", silentContract, techniqueProse, "it survives every assertion", SpecFidelityReport.Kind.WEAK_TEST_ORACLE, true),
                // On a recursion exercise nearly every finding says "recursive" somewhere; an untested base case is ordinary oracle work the downgrade must not swallow.
                Arguments.of("finding merely mentions the mandated technique", mandatingContract, behaviouralProse, "add a discriminator", SpecFidelityReport.Kind.WEAK_TEST_ORACLE,
                        true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("techniqueReclassificationCases")
    void aFindingIsDowngradedOnlyWhenTheContractAndTheFindingBothDemandAnUngradeableTechnique(String scenario, String rulesBody, String findingRequirement, String findingDetail,
            SpecFidelityReport.Kind expectedKind, boolean expectedBlocking) {
        acceptedCandidateWithSpecAndTests(rulesBody);
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, findingRequirement, findingDetail))));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.specFidelityReport().findings()).singleElement().satisfies(finding -> {
                assertThat(finding.kind()).isEqualTo(expectedKind);
                assertThat(finding.isBlocking()).isEqualTo(expectedBlocking);
            });
        }
        // A downgraded finding leaves the loop nothing schedulable, so no repair round is spent on impossible work; a blocking one still buys its round.
        verify(agentLoopRunner, expectedBlocking ? atLeast(2) : times(1)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void anUngradeableTechniqueRuleReachesTheReportWithoutBlockingTheCandidate() {
        // The detector itself is covered in the critic's own tests; what is covered here is the wiring, which nothing else exercises: the orchestrator must call it and merge
        // its findings into the report. An exercise whose brief asks for recursion still awards full marks to an iterative implementation, so this is the only channel that
        // tells the instructor so.
        acceptedCandidateWithSpecAndTests();
        SpecFidelityReport.Finding techniqueRule = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE, "must be recursive",
                "behavioural tests cannot see how a result was produced");
        when(specFidelityCritic.detectUnenforceableTechniqueRules(any())).thenReturn(List.of(techniqueRule));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).as("an ungradeable technique rule is a disclosure, never a rejection").isTrue();
            assertThat(outcome.specFidelityReport().findings()).anySatisfy(finding -> {
                assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.UNENFORCEABLE_TECHNIQUE_RULE);
                assertThat(finding.isBlocking()).isFalse();
                assertThat(finding.requirement()).contains("must be recursive");
            });
        }
    }

    // --- Review availability ---

    @Test
    void aReviewThatCouldNotCompleteIsRetriedInsteadOfEndingTheRepairPhase() {
        // A review that returns no verdict reports "1 blocking quality gap" when it means "we could not review this". The verdict must still fail open, but the WORK must not:
        // a reviewer having a bad turn is not a reason to stop improving the exercise.
        acceptedCandidateWithSpecAndTests();
        SpecFidelityReport unavailable = SpecFidelityReport.qualityReviewUnavailable("the reviewer returned no verdict");
        SpecFidelityReport actionable = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "a wrong parser passes", "add a discriminating assertion")));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(unavailable, actionable, actionable);

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
        }
        // The failed review, the retry, and the reviews after each repair the retry unlocked. The decisive assertion is that repair happened at all, rather than the run ending
        // at the failed review with its whole budget unspent.
        verify(specFidelityCritic, atLeast(2)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(agentLoopRunner, atLeast(2)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void aSecondFailedReviewEndsTheRepairPhaseWithoutLoopingForever() {
        acceptedCandidateWithSpecAndTests();
        SpecFidelityReport unavailable = SpecFidelityReport.qualityReviewUnavailable("the reviewer returned no verdict");
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(unavailable);

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).as("an unreviewable candidate that passed every mechanical gate still stands").isTrue();
        }
        verify(specFidelityCritic, times(2)).critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(agentLoopRunner, times(1)).runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any());
    }

    // --- Contract witnesses ---

    private static final ContractWitness WITNESS = new ContractWitness("R1", "testWitnessNegativeSalary",
            "@Test\nvoid testWitnessNegativeSalary() { assertEquals(0, parse(\"a|b|-5\"), \"negative is invalid\"); }", "accepts negative salary records");

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
        when(verifier.validateContractWitnesses(any(), anyString(), any(), any(), any())).thenReturn(List.of(WITNESS));

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
        when(verifier.validateContractWitnesses(any(), anyString(), any(), any(), any())).thenReturn(List.of());

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.specFidelityReport().findings()).noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE);
        }
    }

    @Test
    void witnessesAreNotAuthoredWhileSomethingStillBlocks() {
        // A repair round is coming that rewrites the artifacts the witnesses were derived from, and a witness is validated against the solution as it stands, so authoring one
        // now re-pays a provider call and a full solution build for a witness that may not survive.
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "rollback", "a plausible wrong implementation survives"))));

        try (GenerationOutcome ignored = generate(() -> false)) {
            verify(specFidelityCritic, never()).authorContractWitnesses(anyString(), anyString(), anyString(), any(), any());
        }
    }

    @Test
    void theWitnessAdoptionPromptOffersTheTestsInsteadOfReportingBlockers() {
        // The `times(2)` also pins the budget: without a round the agent never reads the witness, and more than one would let witnesses drive repeated rewrites of a finished
        // exercise.
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(WITNESS));
        when(verifier.validateContractWitnesses(any(), anyString(), any(), any(), any())).thenReturn(List.of(WITNESS));

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
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "rollback", "a plausible wrong implementation survives")));
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(blocking);
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(WITNESS));
        when(verifier.validateContractWitnesses(any(), anyString(), any(), any(), any())).thenReturn(List.of(WITNESS));

        try (GenerationOutcome ignored = generate(() -> false)) {
            ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
            verify(agentLoopRunner, atLeast(2)).runSession(anyString(), any(), prompts.capture(), any(), anyInt(), any(), any(), any());
            assertThat(prompts.getAllValues().get(1)).as("the first repair addresses the blocker, not the witness").contains("review blockers");
        }
    }

    // --- Termination reason: every exit of the attempt loop names itself, so "budget or convergence" is answerable from a run's artifact rather than from a log line ---

    /**
     * Runs a service whose semantic repair budget is {@code maxSemanticRepairs}, which also derives the attempt cap
     * ({@link GenerationAttemptLoop#MAX_MECHANICAL_ATTEMPTS} + budget + 1). Referenced rather than copied so a change to that arithmetic breaks here instead of silently
     * re-routing a scripted run into a different exit.
     */
    private GenerationOrchestrationService serviceWithRepairBudget(int maxSemanticRepairs) {
        return new GenerationOrchestrationService(Optional.of(sandbox), workspace, agentLoopRunner, verifier, systemPromptService, structuralOracleSeeder, specFidelityCritic,
                jobService, Optional.of(testCaseRepository), 100, maxSemanticRepairs, stagedGenerationRunner, false, stageCheckService, new AgentTranscriptWriter(""),
                new de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry());
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
        // The single most consequential distinction the artifacts could not previously make: a run that stopped because it ran out of rounds, not because it was finished.
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("unbounded blocker"));

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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("unbounded blocker"));

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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("invalid events"));
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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(SpecFidelityReport.qualityReviewUnavailable("the reviewer returned no verdict"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.REVIEW_UNAVAILABLE);
        }
    }

    @Test
    void unchangedResubmittedCandidate_terminatesAsUnchangedCandidateResubmitted() {
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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(contractBlocker);

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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("invalid events"));
        when(structuralOracleSeeder.seedIfStructuralDiff(any(), anyString(), any())).thenReturn(Set.of()).thenThrow(new IllegalStateException("repair extraction failed"));

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

    // The unschedulable-report classifier moved to RepairRoundScheduler with the rest of the repair-phase state machine, and is covered directly (including the
    // failed-review-outranks-a-blocker precedence, which was never pinned here) in RepairRoundSchedulerTest.

    // --- Per-round finding drain: the counts that tell a recurring finding apart from a fresh one of the same category ---

    /** Captures both halves of the progress channel: the human-readable lines, and the structured round telemetry rides on the same events. */
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
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(first, rest);
    }

    @Test
    void theSameFindingAfterARepair_isCountedAsCarriedOverRatherThanFresh() {
        acceptedCandidateReviewedAs(reportWith("emoji graphemes"), reportWith("emoji graphemes"), SpecFidelityReport.empty());

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds).hasSize(3);
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
        assertThat(rounds.get(2)).as("the second repair drained it").satisfies(round -> {
            assertThat(round.carriedOver()).isZero();
            assertThat(round.drained()).isEqualTo(1);
            assertThat(round.fresh()).isZero();
            assertThat(round.blocking()).isZero();
        });
    }

    @Test
    void aDifferentFindingOfTheSameKind_isCountedAsDrainedPlusFreshRatherThanCarriedOver() {
        // The whole point of per-finding identity: both rounds carry exactly one UNCOVERED_REQUIREMENT, so a Kind histogram cannot tell "unrepaired" from "bottomless well".
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
    void aRewordedDetailOnTheSameRequirement_stillCountsAsCarriedOver() {
        // detail is prose the reviewer rewrites freely; treating it as part of the identity would report every finding as fresh and make drain unmeasurable.
        SpecFidelityReport first = new SpecFidelityReport(
                List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "emoji graphemes", "no test covers it")));
        SpecFidelityReport reworded = new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "emoji graphemes",
                "Still nothing asserts this; add an assertion over a multi-codepoint sequence.")));
        acceptedCandidateReviewedAs(first, reworded, SpecFidelityReport.empty());

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds.get(1).carriedOver()).isEqualTo(1);
        assertThat(rounds.get(1).fresh()).isZero();
    }

    @Test
    void aRequirementDifferingOnlyInCasingAndPunctuation_stillCountsAsCarriedOver() {
        acceptedCandidateReviewedAs(reportWith("`emoji` graphemes are not counted"), reportWith("Emoji graphemes are not counted."), SpecFidelityReport.empty());

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds.get(1).carriedOver()).isEqualTo(1);
        assertThat(rounds.get(1).fresh()).isZero();
    }

    @Test
    void aRequirementCitingItsSpecificationRuleLabel_isTheSameFindingAsOneWithout() {
        acceptedCandidateReviewedAs(reportWith("R3 reverse must handle the empty string"), reportWith("reverse must handle the empty string"), SpecFidelityReport.empty());

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds.get(1).carriedOver()).isEqualTo(1);
        assertThat(rounds.get(1).fresh()).isZero();
    }

    @Test
    void aRequirementWhoseTextMerelyStartsWithALetterAndDigits_keepsItsOwnIdentity() {
        acceptedCandidateReviewedAs(reportWith("r2d2 identifiers are rejected"), reportWith("identifiers are rejected"), SpecFidelityReport.empty());

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds.get(1).carriedOver()).isZero();
        assertThat(rounds.get(1).fresh()).isEqualTo(1);
    }

    @Test
    void aFindingUnderADifferentKind_isNotTheSameFinding() {
        acceptedCandidateReviewedAs(reportWith("delegation is not proven"),
                new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "delegation is not proven", "a wrong forwarder passes"))),
                SpecFidelityReport.empty());

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds.get(1).carriedOver()).isZero();
        assertThat(rounds.get(1).drained()).isEqualTo(1);
        assertThat(rounds.get(1).fresh()).isEqualTo(1);
    }

    @Test
    void aMechanicallyRejectedAttempt_isNotAReviewRoundAndDoesNotResetTheBaseline() {
        // A rejected attempt empties the report without asking the reviewer anything. Counting it as a round would report every finding drained precisely when nothing was
        // reviewed, and would then report the same finding as fresh on the next real review.
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(loopSession(completed()));
        when(verifier.verify(any(), anyString(), any(), any(VerificationRequest.class), any(Runnable.class))).thenReturn(accepted(), rejected("the repair broke the build"),
                accepted());
        when(specFidelityCritic.critique(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(reportWith("emoji graphemes"),
                reportWith("emoji graphemes"), SpecFidelityReport.empty());
        when(workspace.extractProblemStatement(any(), anyString())).thenReturn("# Verified", "# Broken repair", "# Corrected repair", "# Final");

        List<ExerciseGenerationRepairRoundDTO> rounds = generateRecordingProgress().rounds;

        assertThat(rounds).extracting(ExerciseGenerationRepairRoundDTO::attempt).as("only the attempts that were actually reviewed produce a round").containsExactly(1, 3, 4);
        assertThat(rounds.get(1)).satisfies(round -> {
            assertThat(round.round()).isEqualTo(2);
            assertThat(round.carriedOver()).as("the baseline survived the unreviewed attempt in between").isEqualTo(1);
            assertThat(round.drained()).isZero();
        });
    }

    @Test
    void theRoundLine_readsAsProgressAndCarriesItsCountsOnTheSameEvent() {
        acceptedCandidateReviewedAs(reportWith("emoji graphemes"), reportWith("CJK graphemes"), SpecFidelityReport.empty());

        RecordingProgressSink sink = generateRecordingProgress();

        assertThat(sink.lines).as("the existing human-readable lines are kept; the telemetry is additive")
                .anySatisfy(line -> assertThat(line).contains("The review found", "1 blocking"));
        assertThat(sink.lines).anySatisfy(line -> assertThat(line).contains("Quality review round 2", "0 still open from the previous round", "1 resolved", "1 new"));
        assertThat(sink.lines).anySatisfy(line -> assertThat(line).contains("Quality review round 3", "no issues remain"));
    }

    @Test
    void aFailingContractWitnessPassCostsTheExerciseNothing() {
        // The pass is advisory scaffolding on an already-accepted candidate; a provider or probe failure must not disturb the verdict.
        acceptedCandidateWithSpecAndTests();
        when(specFidelityCritic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenThrow(new IllegalStateException("provider down"));

        try (GenerationOutcome outcome = generate(() -> false)) {
            assertThat(outcome.isMechanicallyVerified()).isTrue();
            assertThat(outcome.specFidelityReport().findings()).noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.CONTRACT_WITNESS_AVAILABLE);
        }
    }
}
