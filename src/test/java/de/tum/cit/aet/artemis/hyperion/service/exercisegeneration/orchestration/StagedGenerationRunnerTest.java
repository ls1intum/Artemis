package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.stubbing.Answer;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationActivityDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.FakeInteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentSystemPromptService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentTranscriptWriter;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationActivityTracker;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationStage;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.SandboxAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ApprovedSpecRegistry;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Orchestration tests for the three-phase specification, coherent executable-build, and statement workflow. */
class StagedGenerationRunnerTest {

    private static final String VALID_SPEC_DOCUMENT = """
            # Exercise

            ## Rules
            - R1: computes a result from the input.

            ## Worked Examples
            | Rules | Input | Expected |
            |---|---|---|
            | R1 | 2 | 4 |
            | R1 | 3 | 9 |

            ## Design
            | Type | Role | Template status |
            |---|---|---|
            | Calculator | computes the result | stubbed |

            ## Public API
            `Calculator`: `int calculate(int input)`

            ## Testing Strategy
            | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
            |---|---|---|---|---|
            | S1 | Calculator | typical and zero | 3 | no |

            ## Contract Risk Inventory
            | Seam | Rules | Admitted partitions | Excluded inputs |
            |---|---|---|---|
            | S1 | R1 | S1.P1: zero and positive values | negative values |

            ## Diagram
            no — single-class exercise
            """;

    private static final String VALID_TEST_PLAN = "{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";

    private static final BooleanSupplier NEVER_CANCELLED = () -> false;

    /** The staged runner reads three workspace files back between stages and probes the tree layout; everything else the shared fake already models. */
    private static final class FakeSandbox extends FakeInteractiveSandbox {

        private String specMarkdown = VALID_SPEC_DOCUMENT;

        private String testPlanJson = VALID_TEST_PLAN;

        private String problemStatement = "# Title\n\n[task][Do the thing](testFoo)\nImplement the calculator operation.";

        private final String layout = "solution/pom.xml\ntemplate/pom.xml\ntests/pom.xml";

        @Override
        protected SandboxExecResultDTO respond(String[] command) {
            if (command.length >= 2 && "cat".equals(command[0])) {
                String path = command[1];
                if (path.endsWith("SPEC.md")) {
                    return specMarkdown == null ? missing() : found(specMarkdown);
                }
                if (path.endsWith("test-plan.json")) {
                    return testPlanJson == null ? missing() : found(testPlanJson);
                }
                if (path.endsWith("problem-statement.md")) {
                    return problemStatement == null ? missing() : found(problemStatement);
                }
            }
            if (command.length >= 3 && "sh".equals(command[0]) && command[2].contains("find") && command[2].contains("head -80")) {
                return found(layout);
            }
            if (command.length > 0 && "grep".equals(command[0])) {
                return found("TODO S1:");
            }
            return found("");
        }

        private static SandboxExecResultDTO found(String value) {
            return new SandboxExecResultDTO(0, value, "", false);
        }

        private static SandboxExecResultDTO missing() {
            return new SandboxExecResultDTO(1, "", "not found", false);
        }
    }

    private AgentLoopRunner agentLoopRunner;

    private AgentSystemPromptService systemPromptService;

    private DifferentialVerificationService verifier;

    private ApprovedSpecRegistry approvedSpecs;

    private StageCheckService stageCheckService;

    private SandboxAgentTools baseTools;

    private ProgrammingExercise exercise;

    private FakeSandbox sandbox;

    private StagedGenerationRunner runner;

    private AtomicReference<Supplier<SeededStructuralTests>> structuralRefresh;

    private AtomicReference<SeededStructuralTests> currentStructuralTests;

    @BeforeEach
    void setUp() {
        agentLoopRunner = mock(AgentLoopRunner.class);
        systemPromptService = mock(AgentSystemPromptService.class);
        verifier = mock(DifferentialVerificationService.class);
        approvedSpecs = new ApprovedSpecRegistry();
        stageCheckService = new StageCheckService(verifier, approvedSpecs);
        baseTools = mock(SandboxAgentTools.class);
        structuralRefresh = new AtomicReference<>(() -> SeededStructuralTests.EMPTY);
        currentStructuralTests = new AtomicReference<>(SeededStructuralTests.EMPTY);
        doAnswer(invocation -> {
            structuralRefresh.set(invocation.getArgument(0));
            return null;
        }).when(baseTools).configureStructuralOracleRefresh(any());
        when(baseTools.refreshStructuralOracle()).thenAnswer(invocation -> {
            SeededStructuralTests refreshed = structuralRefresh.get().get();
            currentStructuralTests.set(refreshed);
            return refreshed;
        });
        when(baseTools.seededStructuralTests()).thenAnswer(invocation -> currentStructuralTests.get());
        when(baseTools.seededStructuralTestNames()).thenReturn(Set.of());
        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(1L);
        when(systemPromptService.buildStage(any(), any())).thenReturn("STAGE SYSTEM PROMPT");
        sandbox = new FakeSandbox();
        when(baseTools.writeFile(eq("SPEC.md"), anyString())).thenAnswer(invocation -> {
            sandbox.specMarkdown = invocation.getArgument(1);
            return "Wrote SPEC.md";
        });
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, "FRESH");
    }

    private static AgentLoopResult completed(int turns, String message) {
        return new AgentLoopResult(AgentLoopResult.Status.COMPLETED, turns, message);
    }

    private static SeededStructuralTests structuralTests(Set<String> names) {
        return new SeededStructuralTests(names, Map.of("test/de/tum/cit/aet/artemis/TrustedStructuralTest.java", "// server-owned test fixture"));
    }

    private static AgentVerifyReport passingReport(String... names) {
        List<String> exactNames = List.of(names);
        return new AgentVerifyReport(exactNames.size(), true, List.of(), List.of(), exactNames.size(), true, true, List.of(), List.of(), exactNames, List.of(), List.of(), true,
                List.of());
    }

    private static AgentVerifyReport failingReport() {
        return new AgentVerifyReport(1, false, List.of("testFoo"), List.of(), 1, true, false, List.of(), List.of(), List.of("testFoo"), List.of(), List.of(), false,
                List.of("the solution does not pass"));
    }

    private AgentLoopResult run(BooleanSupplier cancelled, Supplier<SeededStructuralTests> structuralSeedHook) {
        return runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", cancelled, null, null, structuralSeedHook).result();
    }

    @Test
    void runsSpecificationExecutableBuildAndStatementInOrder() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(2, "spec"), completed(10, "build"),
                completed(3, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));
        AtomicInteger structuralRefreshes = new AtomicInteger();

        AgentLoopResult result = run(NEVER_CANCELLED, () -> {
            structuralRefreshes.incrementAndGet();
            return SeededStructuralTests.EMPTY;
        });

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(15);
        assertThat(result.finalMessage()).isEqualTo("statement");
        assertThat(structuralRefreshes).hasValue(1);
        InOrder order = inOrder(baseTools, systemPromptService, agentLoopRunner);
        for (GenerationStage stage : List.of(GenerationStage.SPEC, GenerationStage.TESTS, GenerationStage.STATEMENT)) {
            order.verify(baseTools).enterStage(stage);
            order.verify(systemPromptService).buildStage(exercise, stage);
            order.verify(agentLoopRunner).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        }
    }

    @Test
    void executableBuildFailureGetsOneBoundedReentryAndStillUsesTheReservedStatementPhase() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(2, "build one"),
                completed(2, "build two"), completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(failingReport());

        AgentLoopResult result = run(NEVER_CANCELLED, () -> SeededStructuralTests.EMPTY);

        assertThat(result.finalMessage()).isEqualTo("statement");
        verify(agentLoopRunner, times(4)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(baseTools).enterStage(GenerationStage.STATEMENT);
    }

    @Test
    void continuousGateRetryStartsFromCurrentArtifactsAndFeedbackInsteadOfTheFailedTrajectory() {
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, "CONTINUOUS");
        List<Message> specificationConversation = List.of(new UserMessage("specification trajectory"));
        List<Message> failedBuildConversation = List.of(new UserMessage("stale build assumption"));
        List<Message> retryConversation = List.of(new UserMessage("repaired build trajectory"));
        when(agentLoopRunner.runSession(anyString(), any(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(
                new AgentLoopRunner.AgentLoopSession(completed(1, "spec"), specificationConversation),
                new AgentLoopRunner.AgentLoopSession(completed(2, "build one"), failedBuildConversation),
                new AgentLoopRunner.AgentLoopSession(completed(2, "build two"), retryConversation),
                new AgentLoopRunner.AgentLoopSession(completed(1, "statement"), List.of(new UserMessage("statement trajectory"))));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(failingReport());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> priorConversations = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);

        AgentLoopResult result = run(NEVER_CANCELLED, () -> SeededStructuralTests.EMPTY);

        assertThat(result.finalMessage()).isEqualTo("statement");
        verify(agentLoopRunner, times(4)).runSession(anyString(), priorConversations.capture(), prompts.capture(), any(), anyInt(), any(), any(), any());
        assertThat(priorConversations.getAllValues()).containsOnlyNulls();
        assertThat(prompts.getAllValues().get(2)).contains("CURRENT SPEC.md", "CURRENT WORKSPACE LAYOUT", "GATE FEEDBACK FROM THE PREVIOUS ATTEMPT", "the solution does not pass");
        verify(baseTools, times(2)).enterStage(GenerationStage.TESTS);
        verify(baseTools).enterStage(GenerationStage.STATEMENT);
    }

    @Test
    void statementReceivesOnlyTheVisibleTypedPlanHandoff() {
        sandbox.specMarkdown = VALID_SPEC_DOCUMENT.replace("| S1 | Calculator | typical and zero | 3 | no |", "| S1 | Calculator | typical and zero | 3 | yes |");
        sandbox.testPlanJson = "{\"tests\":[{\"name\":\"visibleCase\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"},"
                + "{\"name\":\"hiddenCase\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"AFTER_DUE_DATE\"}]}";
        sandbox.problemStatement = "# Title\n\n[task][Do the thing](visibleCase)\nImplement it.";
        when(exercise.getDueDate()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(4, "build"),
                completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("visibleCase", "hiddenCase"));
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);

        runner.run(exercise, baseTools, baseTools, "raw brief\n\nINITIAL WORKSPACE LAYOUT: reference/problem-statement.md", "raw brief", Map.of(), sandbox, "s", NEVER_CANCELLED,
                null, null, () -> SeededStructuralTests.EMPTY, true, null);

        verify(agentLoopRunner, times(3)).run(anyString(), prompts.capture(), any(), anyInt(), any(), any(), any());
        String statementPrompt = prompts.getAllValues().get(2);
        assertThat(statementPrompt).contains("raw brief", "ACCEPTED STATEMENT HANDOFF", "`[task][Student-facing title](exactTestName)`",
                "Bind every visible test below exactly once", "S1: visibleCase")
                .doesNotContain("INITIAL WORKSPACE LAYOUT", "reference/problem-statement.md", "hiddenCase", "MECHANICAL PRECHECK");
    }

    @Test
    void statementHandoffAndGateUseEveryAuthoritativeStructuralCheckEvenWhenTheBuildReportOmitsThem() {
        Set<String> structuralChecks = Set.of("testMethods[Calculator]", "testClass[Calculator]", "testConstructors[Calculator]");
        when(baseTools.seededStructuralTestNames()).thenReturn(structuralChecks);
        sandbox.problemStatement = "# Title\n\n[task][Create the calculator](testFoo,testClass[Calculator],testConstructors[Calculator],testMethods[Calculator])\n"
                + "Create the calculator type and implement its operation.";
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(4, "build"),
                completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), eq(structuralTests(structuralChecks)))).thenReturn(passingReport("testFoo"));
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);

        AgentLoopResult result = run(NEVER_CANCELLED, () -> structuralTests(structuralChecks));

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(agentLoopRunner, times(3)).run(anyString(), prompts.capture(), any(), anyInt(), any(), any(), any());
        assertThat(prompts.getAllValues().get(2)).contains("Server-seeded structural checks grouped by owner type", "all are visible and must also be bound exactly once",
                "Calculator: testClass[Calculator], testConstructors[Calculator], testMethods[Calculator]", "task whose work creates or declares that owner type/API",
                "never duplicate them");
    }

    @Test
    void specificationHasOneSharedThreeRefinementBudget() {
        sandbox.specMarkdown = "# incomplete";
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "invalid"));

        AgentLoopResult result = run(NEVER_CANCELLED, () -> SeededStructuralTests.EMPTY);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(agentLoopRunner, times(4)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(baseTools, never()).enterStage(GenerationStage.TESTS);
        assertThat(approvedSpecs.approved("s")).isEmpty();
    }

    @Test
    void semanticSpecificationReviewRefinesBeforeFreezing() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        SpecFidelityCriticService.SpecificationReview firstReview = new SpecFidelityCriticService.SpecificationReview(true, List.of("the collaboration is incomplete"));
        when(reviewer.reviewSpecification(anyString(), anyString(), any(), any())).thenReturn(firstReview);
        when(reviewer.reviewSpecification(anyString(), isNull(), anyString(), eq(firstReview), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(true, List.of()));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "first spec"), completed(1, VALID_SPEC_DOCUMENT),
                completed(3, "build"), completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);

        AgentLoopResult result = run(NEVER_CANCELLED, () -> SeededStructuralTests.EMPTY);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(reviewer).reviewSpecification(eq("brief"), anyString(), any(), any());
        verify(reviewer).reviewSpecification(eq("brief"), isNull(), anyString(), eq(firstReview), any(), any());
        verify(agentLoopRunner, times(4)).run(anyString(), prompts.capture(), any(), anyInt(), any(), any(), any());
        assertThat(prompts.getAllValues().get(1)).contains("collaboration is incomplete");
        assertThat(approvedSpecs.approved("s")).contains(VALID_SPEC_DOCUMENT);
    }

    @Test
    void exhaustedSpecificationReviewFindingsRemainAttachedToTheStagedOutcome() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        SpecFidelityCriticService.SpecificationReview unresolvedReview = new SpecFidelityCriticService.SpecificationReview(true,
                List.of("R1 still invents a source-level technique"));
        when(reviewer.reviewSpecification(anyString(), anyString(), any(), any())).thenReturn(unresolvedReview);
        when(reviewer.reviewSpecification(anyString(), isNull(), anyString(), any(), any(), any())).thenReturn(unresolvedReview);
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec one"), completed(1, "spec two"),
                completed(1, "spec three"), completed(1, "spec four"), completed(3, "build"), completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        StagedGenerationRunner.StagedRunOutcome outcome = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null,
                () -> SeededStructuralTests.EMPTY);

        assertThat(outcome.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(outcome.unresolvedSpecificationFindings()).containsExactly("R1 still invents a source-level technique");
        verify(reviewer).reviewSpecification(eq("brief"), anyString(), any(), any());
        verify(reviewer, times(3)).reviewSpecification(eq("brief"), isNull(), anyString(), any(), any(), any());
    }

    @Test
    void repeatedLearningFitDirectionReselectsInsteadOfOptimizingAnotherRewrite() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        ExerciseConceptSelector conceptSelector = mock(ExerciseConceptSelector.class);
        ExerciseConceptSelector.ConceptSelection initialConcept = new ExerciseConceptSelector.ConceptSelection(true, 1, "initial concept", 1, List.of(), "", "");
        ExerciseConceptSelector.ConceptSelection replacementConcept = new ExerciseConceptSelector.ConceptSelection(true, 2, "replacement concept", 1, List.of(), "", "");
        when(conceptSelector.select(eq("brief"), any(), any(), any())).thenReturn(initialConcept);
        when(conceptSelector.select(eq("brief"), anyString(), any(), any(), any())).thenReturn(replacementConcept);
        SpecFidelityCriticService.SpecificationReview firstReview = new SpecFidelityCriticService.SpecificationReview(true, false, true, List.of("too shallow once"), "",
                "TOO_SHALLOW");
        SpecFidelityCriticService.SpecificationReview secondReview = new SpecFidelityCriticService.SpecificationReview(true, false, true, List.of("too shallow again"), "",
                "TOO_SHALLOW");
        when(reviewer.reviewSpecification(eq("brief"), anyString(), anyString(), any(), any())).thenReturn(firstReview,
                new SpecFidelityCriticService.SpecificationReview(true, false, false, List.of(), "", "SUFFICIENT"));
        when(reviewer.reviewSpecification(eq("brief"), anyString(), anyString(), eq(firstReview), any(), any())).thenReturn(secondReview);
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer, conceptSelector,
                "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, VALID_SPEC_DOCUMENT), completed(1, VALID_SPEC_DOCUMENT),
                completed(1, "# SPEC.md\n" + VALID_SPEC_DOCUMENT), completed(3, "build"), completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        AgentLoopResult result = run(NEVER_CANCELLED, () -> SeededStructuralTests.EMPTY);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(conceptSelector).select(eq("brief"), anyString(), any(), any(), any());
        verify(reviewer, times(2)).reviewSpecification(eq("brief"), anyString(), anyString(), any(), any());
        verify(reviewer).reviewSpecification(eq("brief"), anyString(), anyString(), any(), any(), any());
        assertThat(approvedSpecs.approved("s")).hasValueSatisfying(specification -> assertThat(specification).contains(VALID_SPEC_DOCUMENT.strip()));
    }

    /**
     * Re-entry must never leave the run holding less than it already had. The specification used to be cleared before replacement discovery ran, so a reviewer that was
     * unavailable on re-entry destroyed a reviewed specification and took the whole generation down with it — while the very same unavailability on the first selection is
     * tolerated and continues from the brief. Discovery now runs first, and an empty result keeps the incumbent concept and its specification.
     */
    @Test
    void aReplacementConceptThatNeverArrivesKeepsTheSpecificationTheRunAlreadyReviewed() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        ExerciseConceptSelector conceptSelector = mock(ExerciseConceptSelector.class);
        when(conceptSelector.select(eq("brief"), any(), any(), any())).thenReturn(new ExerciseConceptSelector.ConceptSelection(true, 1, "initial concept", 1, List.of(), "", ""));
        when(conceptSelector.select(eq("brief"), anyString(), any(), any(), any()))
                .thenReturn(new ExerciseConceptSelector.ConceptSelection(false, null, null, 1, List.of(), "Concept review was unavailable.", ""));
        when(reviewer.reviewSpecification(eq("brief"), anyString(), anyString(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(true, true, true, List.of("the central interaction cannot carry the objective"), "", "MISALIGNED"));
        when(reviewer.reviewSpecification(eq("brief"), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(true, false, false, List.of(), "", "SUFFICIENT"));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer, conceptSelector,
                "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, VALID_SPEC_DOCUMENT), completed(1, VALID_SPEC_DOCUMENT),
                completed(3, "build"), completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        StagedGenerationRunner.StagedRunOutcome outcome = runner.run(exercise, baseTools, baseTools, "brief", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null,
                () -> SeededStructuralTests.EMPTY, true, true, null);

        assertThat(outcome.result().status()).isNotEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(approvedSpecs.approved("s")).hasValueSatisfying(specification -> assertThat(specification).contains(VALID_SPEC_DOCUMENT.strip()));
        assertThat(outcome.unresolvedConceptFindings()).isNotEmpty().first().asString().contains("asked for a different exercise concept", "produced no candidate to switch to");
        assertThat(outcome.unresolvedConceptFindings()).contains("the central interaction cannot carry the objective");
        // One replacement attempt only: the reviewer asks for reselection again on the next round, and retrying spends discovery turns to reach the same unavailable reviewer.
        verify(conceptSelector, times(1)).select(eq("brief"), anyString(), any(), any(), any());
    }

    @Test
    void authoritativeStatementRefinesItsContractWithoutReplacingTheInstructorConcept() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        ExerciseConceptSelector conceptSelector = mock(ExerciseConceptSelector.class);
        SpecFidelityCriticService.SpecificationReview firstReview = new SpecFidelityCriticService.SpecificationReview(true, false, true,
                List.of("make the global tie rule explicit"), "", "TOO_SHALLOW");
        when(reviewer.reviewSpecification(eq("authoritative elevator statement"), anyString(), any(), any())).thenReturn(firstReview);
        when(reviewer.reviewSpecification(eq("authoritative elevator statement"), isNull(), anyString(), eq(firstReview), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(true, false, false, List.of(), "", "SUFFICIENT"));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer, conceptSelector,
                "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, VALID_SPEC_DOCUMENT), completed(1, VALID_SPEC_DOCUMENT),
                completed(3, "build"), completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        StagedGenerationRunner.StagedRunOutcome outcome = runner.run(exercise, baseTools, baseTools, "authoritative elevator statement", "authoritative elevator statement",
                Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, () -> SeededStructuralTests.EMPTY, true, false, null);

        assertThat(outcome.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(reviewer).reviewSpecification(eq("authoritative elevator statement"), anyString(), any(), any());
        verify(reviewer).reviewSpecification(eq("authoritative elevator statement"), isNull(), anyString(), eq(firstReview), any(), any());
        verifyNoInteractions(conceptSelector);
        assertThat(approvedSpecs.approved("s")).contains(VALID_SPEC_DOCUMENT);
    }

    @Test
    void unavailableOptionalConceptReviewFallsBackToTheBriefAndMandatorySpecificationReview() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        ExerciseConceptSelector conceptSelector = mock(ExerciseConceptSelector.class);
        when(conceptSelector.select(eq("brief"), any(), any(), any()))
                .thenReturn(new ExerciseConceptSelector.ConceptSelection(false, null, null, 1, List.of(), "Concept review was unavailable.", ""));
        when(reviewer.reviewSpecification(eq("brief"), anyString(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(true, false, false, List.of(), "", "SUFFICIENT"));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer, conceptSelector,
                "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, VALID_SPEC_DOCUMENT), completed(3, "build"),
                completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        AgentLoopResult result = run(NEVER_CANCELLED, () -> SeededStructuralTests.EMPTY);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(reviewer).reviewSpecification(eq("brief"), anyString(), any(), any());
        assertThat(approvedSpecs.approved("s")).contains(VALID_SPEC_DOCUMENT);
    }

    @Test
    void completedConceptRejectionHasItsOwnTerminationReason() {
        ExerciseConceptSelector conceptSelector = mock(ExerciseConceptSelector.class);
        when(conceptSelector.select(eq("brief"), any(), any(), any()))
                .thenReturn(new ExerciseConceptSelector.ConceptSelection(true, null, null, 2, List.of(), "Every candidate invented a constraint.", ""));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs,
                mock(SpecFidelityCriticService.class), conceptSelector, "FRESH");

        StagedGenerationRunner.StagedRunOutcome outcome = runner.run(exercise, baseTools, baseTools, "brief", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null,
                () -> SeededStructuralTests.EMPTY, true, true, null);

        assertThat(outcome.result().status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(outcome.terminationReason()).isEqualTo(TerminationReason.NO_ADMISSIBLE_CONCEPT);
        assertThat(outcome.result().finalMessage()).contains("No exercise concept passed", "Every candidate invented a constraint");
        verifyNoInteractions(agentLoopRunner);
    }

    @Test
    void aRejectedConceptWithAFallbackIsBuiltAnywayInsteadOfProducingNothing() {
        // The gate still admitted no candidate, but a completed rejection is a design objection rather than a failure, so the run produces a draft plus the objections.
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        when(reviewer.reviewSpecification(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(true, false, false, List.of(), "", "SUFFICIENT"));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer,
                rejectingSelectorWithFallback(), "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(3, "build"),
                completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        StagedGenerationRunner.StagedRunOutcome outcome = runner.run(exercise, baseTools, baseTools, "brief", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null,
                () -> SeededStructuralTests.EMPTY, true, true, null);

        assertThat(outcome.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(outcome.terminationReason()).isNull();
        assertThat(approvedSpecs.approved("s")).isPresent();
        verify(baseTools).enterStage(GenerationStage.TESTS);
        verify(baseTools).enterStage(GenerationStage.STATEMENT);
    }

    @Test
    void everyObjectionRaisedAgainstTheFallbackConceptSurvivesAsAnInstructorNote() {
        // Both reviewer voices reach the instructor verbatim: the selector's per-candidate comparison and the focused admission audit.
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        when(reviewer.reviewSpecification(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(true, false, false, List.of(), "", "SUFFICIENT"));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer,
                rejectingSelectorWithFallback(), "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(3, "build"),
                completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        StagedGenerationRunner.StagedRunOutcome outcome = runner.run(exercise, baseTools, baseTools, "brief", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null,
                () -> SeededStructuralTests.EMPTY, true, true, null);

        assertThat(outcome.unresolvedConceptFindings()).hasSize(3);
        assertThat(outcome.unresolvedConceptFindings().getFirst()).contains("admitted no candidate", "candidate 2", "rejected least");
        assertThat(outcome.unresolvedConceptFindings()).contains("Candidate 2: difficulty — only one numeric method")
                .contains("Selected concept failed focused admission: introduces a fixed capacity limit the brief does not require");
        // A concept objection is not a specification objection; conflating the two mislabels the note the instructor has to act on.
        assertThat(outcome.unresolvedSpecificationFindings()).isEmpty();
    }

    @Test
    void objectionsAgainstTheFallbackConceptSurviveAStageFailureLaterInTheRun() {
        // Every exit of the stage machine carries them, so a run that died at a later gate can still say which contested design it was building.
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        when(reviewer.reviewSpecification(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(true, false, false, List.of(), "", "SUFFICIENT"));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer,
                rejectingSelectorWithFallback(), "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"),
                new AgentLoopResult(AgentLoopResult.Status.ERROR, 2, "the provider failed"));

        StagedGenerationRunner.StagedRunOutcome outcome = runner.run(exercise, baseTools, baseTools, "brief", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null,
                () -> SeededStructuralTests.EMPTY, true, true, null);

        assertThat(outcome.result().status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(outcome.unresolvedConceptFindings()).isNotEmpty();
    }

    @Test
    void theFallbackConceptIsWhatTheSpecificationStageIsAskedToInstantiate() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        when(reviewer.reviewSpecification(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(true, false, false, List.of(), "", "SUFFICIENT"));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer,
                rejectingSelectorWithFallback(), "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(3, "build"),
                completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        runner.run(exercise, baseTools, baseTools, "brief", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, () -> SeededStructuralTests.EMPTY, true, true, null);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(agentLoopRunner, times(3)).run(anyString(), prompt.capture(), any(), anyInt(), any(), any(), any());
        assertThat(prompt.getAllValues().getFirst()).contains("a bounded stack over a fixed-size array");
    }

    /** A completed review that admitted nothing but named candidate 2 as the one it rejected least, carrying both reviewers' objections. */
    private static ExerciseConceptSelector rejectingSelectorWithFallback() {
        ExerciseConceptSelector conceptSelector = mock(ExerciseConceptSelector.class);
        ExerciseConceptSelector.ConceptFallback fallback = new ExerciseConceptSelector.ConceptFallback(
                "## Candidate 2\nCentral interaction: a bounded stack over a fixed-size array.", 2, 1, List.of("Candidate 2: difficulty — only one numeric method",
                        "Selected concept failed focused admission: introduces a fixed capacity limit the brief does not require"));
        when(conceptSelector.select(eq("brief"), any(), any(), any()))
                .thenReturn(new ExerciseConceptSelector.ConceptSelection(true, null, null, 2, List.of(), "No candidate was admitted.", "", fallback));
        return conceptSelector;
    }

    @Test
    void unavailableSpecificationReviewFailsOpenAndFreezesTheMechanicallyValidSpec() {
        // Fail open on the subjective axis: a reviewer that cannot return a well-formed verdict must not discard a specification that already passed the mechanical gate.
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        when(reviewer.reviewSpecification(anyString(), anyString(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(false, false, false, List.of(), "malformed verdict"));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(4, "build"),
                completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        StagedGenerationRunner.StagedRunOutcome outcome = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null,
                () -> SeededStructuralTests.EMPTY);

        assertThat(outcome.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(outcome.unresolvedSpecificationFindings()).singleElement().asString().contains("quality review was inconclusive", "instructor review");
        assertThat(approvedSpecs.approved("s")).isPresent();
        verify(baseTools).enterStage(GenerationStage.TESTS);
        verify(baseTools).enterStage(GenerationStage.STATEMENT);
    }

    @Test
    void inconclusiveSpecificationReviewPreservesItsGroundedRiskHistoryForInstructorReview() {
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        List<String> groundedRisks = List.of("Omission — brief requires elevators never move but SPEC does not preserve that guarantee",
                "Ambiguity — int distance subtraction can overflow at the admitted extremes");
        when(reviewer.reviewSpecification(anyString(), anyString(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(false, false, false, List.of(), "correction response malformed", null, groundedRisks));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(4, "build"),
                completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        StagedGenerationRunner.StagedRunOutcome outcome = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null,
                () -> SeededStructuralTests.EMPTY);

        assertThat(outcome.unresolvedSpecificationFindings()).hasSize(2)
                .allSatisfy(finding -> assertThat(finding).startsWith("Unresolved specification-review hypothesis from grounded evidence:"));
        assertThat(outcome.unresolvedSpecificationFindings()).anyMatch(finding -> finding.contains("elevators never move"))
                .anyMatch(finding -> finding.contains("distance subtraction can overflow"));
    }

    @Test
    void failedBestSpecificationRestoreKeepsUncertaintyAttachedToTheDraftActuallyApproved() {
        String measuredDraft = VALID_SPEC_DOCUMENT + "\n<!-- measured -->\n";
        String unmeasuredDraft = VALID_SPEC_DOCUMENT + "\n<!-- unmeasured -->\n";
        sandbox.specMarkdown = measuredDraft;
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        SpecFidelityCriticService.SpecificationReview firstReview = new SpecFidelityCriticService.SpecificationReview(true, List.of("measured concern"));
        when(reviewer.reviewSpecification(anyString(), anyString(), any(), any())).thenReturn(firstReview);
        when(reviewer.reviewSpecification(anyString(), isNull(), anyString(), eq(firstReview), any(), any()))
                .thenReturn(new SpecFidelityCriticService.SpecificationReview(false, false, false, List.of(), "malformed verdict"));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer, "FRESH");
        AtomicInteger calls = new AtomicInteger();
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                sandbox.specMarkdown = unmeasuredDraft;
            }
            return completed(1, "done");
        });
        when(baseTools.writeFile("SPEC.md", measuredDraft)).thenReturn("ERROR: workspace unavailable");
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        StagedGenerationRunner.StagedRunOutcome outcome = runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null,
                () -> SeededStructuralTests.EMPTY);

        assertThat(outcome.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(approvedSpecs.approved("s")).contains(unmeasuredDraft);
        assertThat(outcome.unresolvedSpecificationFindings()).singleElement().asString().contains("quality review was inconclusive").doesNotContain("measured concern");
    }

    @Test
    void specificationReadBackFailureAfterTheGateFailsClosedBeforeApproval() {
        stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(any(), any(), anyString(), any(), anyMap(), any(), any(SeededStructuralTests.class))).thenReturn(StageCheckResult.passed("SPEC gate passed"));
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, "FRESH");
        sandbox.specMarkdown = null;
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"));

        AgentLoopResult result = run(NEVER_CANCELLED, () -> SeededStructuralTests.EMPTY);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(result.finalMessage()).contains("could not be read back", "unfrozen contract");
        assertThat(approvedSpecs.approved("s")).isEmpty();
        verify(baseTools, never()).enterStage(GenerationStage.TESTS);
    }

    @Test
    void approvedSpecificationIsPublishedToTheSink() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(2, "build"),
                completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));
        AtomicReference<String> published = new AtomicReference<>();

        runner.run(exercise, baseTools, baseTools, "brief", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, () -> SeededStructuralTests.EMPTY, true, published::set);

        assertThat(published).hasValue(VALID_SPEC_DOCUMENT);
        assertThat(approvedSpecs.approved("s")).contains(VALID_SPEC_DOCUMENT);
    }

    /**
     * Each authoring stage announces itself once as the run leaves it, so the orchestrator can snapshot the repositories while the sandbox is still alive. Without this, a run
     * that dies before verification has never captured anything: the only durable snapshot is the mechanically verified checkpoint, taken after verification passes.
     */
    @Test
    void everyAuthoringStageAnnouncesItsBoundaryOnceAsTheRunLeavesIt() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(2, "build"),
                completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));
        List<GenerationStage> boundaries = new java.util.ArrayList<>();

        runner.run(exercise, baseTools, baseTools, "brief", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, () -> SeededStructuralTests.EMPTY, true, true, null,
                boundaries::add);

        assertThat(boundaries).containsExactly(GenerationStage.SPEC, GenerationStage.TESTS, GenerationStage.STATEMENT);
    }

    /** A stage that stops the run never reaches its boundary, so nothing is snapshotted for a stage that produced no accepted output. */
    @Test
    void aStageThatStopsTheRunAnnouncesNoBoundary() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"));
        sandbox.specMarkdown = null;
        List<GenerationStage> boundaries = new java.util.ArrayList<>();

        runner.run(exercise, baseTools, baseTools, "brief", "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, null, () -> SeededStructuralTests.EMPTY, true, true, null,
                boundaries::add);

        assertThat(boundaries).isEmpty();
    }

    @Test
    void cancellationBetweenPhasesStopsBeforeTheNextAgentCall() {
        AtomicInteger completedAgentCalls = new AtomicInteger();
        BooleanSupplier cancelled = () -> completedAgentCalls.get() >= 1;
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            completedAgentCalls.incrementAndGet();
            return completed(2, "spec");
        });

        AgentLoopResult result = run(cancelled, () -> SeededStructuralTests.EMPTY);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void wallClockBudgetStopsBeforeStartingAnotherPhase() {
        // At the default PT30M deadline the authoring budget is 22 minutes, so an elapsed 23 minutes stops the run before the next phase.
        AtomicBoolean firstPhaseCompleted = new AtomicBoolean();
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            firstPhaseCompleted.set(true);
            return completed(2, "spec");
        });
        Instant start = Instant.parse("2026-07-23T10:00:00Z");
        runner.setClockForTests(() -> firstPhaseCompleted.get() ? start.plus(Duration.ofMinutes(23)) : start);

        AgentLoopResult result = run(NEVER_CANCELLED, () -> SeededStructuralTests.EMPTY);

        assertThat(result.turns()).isEqualTo(2);
        verify(agentLoopRunner, times(1)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void raisingTheConfiguredJobDeadlineExtendsThePhaseThatWouldOtherwiseStop() {
        // The companion of the test above, differing only in the configured deadline: only a budget derived from artemis.hyperion.agent.max-job-duration lets the same elapsed 23
        // minutes reach all three stages, so any private wall-clock ceiling at or below 23 minutes fails here.
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, null, null, "FRESH",
                Duration.ofMinutes(60));
        AtomicBoolean firstPhaseCompleted = new AtomicBoolean();
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
            firstPhaseCompleted.set(true);
            return completed(2, "phase");
        });
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));
        Instant start = Instant.parse("2026-07-23T10:00:00Z");
        runner.setClockForTests(() -> firstPhaseCompleted.get() ? start.plus(Duration.ofMinutes(23)) : start);

        AgentLoopResult result = run(NEVER_CANCELLED, () -> SeededStructuralTests.EMPTY);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(agentLoopRunner, times(3)).run(anyString(), anyString(), any(), anyInt(), any(), any(), any());
        verify(baseTools).enterStage(GenerationStage.STATEMENT);
    }

    @Test
    void theAuthoringBudgetReservesAFixedTailOfTheConfiguredJobDeadline() {
        assertThat(StagedGenerationRunner.authoringBudget(Duration.ofMinutes(30))).isEqualTo(Duration.ofMinutes(22));
        // The reserve protects one differential verification pass, which costs the same however long a job may run, so a raised deadline is passed through in full.
        assertThat(StagedGenerationRunner.authoringBudget(Duration.ofMinutes(60))).isEqualTo(Duration.ofMinutes(52));
        assertThat(StagedGenerationRunner.authoringBudget(Duration.ofMinutes(20))).isEqualTo(Duration.ofMinutes(12));
        // Below twice the reserve, holding the whole tail back would leave less authoring time than tail; half the deadline is kept instead of none.
        assertThat(StagedGenerationRunner.authoringBudget(Duration.ofMinutes(10))).isEqualTo(Duration.ofMinutes(5));
        assertThat(StagedGenerationRunner.authoringBudget(Duration.ofMinutes(1))).isEqualTo(Duration.ofSeconds(30));
        assertThatThrownBy(() -> StagedGenerationRunner.authoringBudget(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-job-duration must be positive");
        assertThatThrownBy(() -> StagedGenerationRunner.authoringBudget(Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allocationAppliesFloorRolloverAndRemainingCap() {
        assertThat(StagedGenerationRunner.allocateStageBudget(7, 0, 83)).isEqualTo(7);
        assertThat(StagedGenerationRunner.allocateStageBudget(54, 5, 60)).isEqualTo(59);
        assertThat(StagedGenerationRunner.allocateStageBudget(7, 20, 4)).isEqualTo(4);
        assertThat(StagedGenerationRunner.allocateStageBudget(7, 0, 2)).isEqualTo(3);
    }

    @Test
    void earlierStagesCannotConsumeTheStatementPass() {
        assertThat(StagedGenerationRunner.allocatablePool(GenerationStage.TESTS, 21)).isEqualTo(14);
        assertThat(StagedGenerationRunner.allocatablePool(GenerationStage.SPEC, 7)).isZero();
        assertThat(StagedGenerationRunner.allocatablePool(GenerationStage.STATEMENT, 7)).isEqualTo(7);
    }

    @Test
    void rejectsUnknownConversationMode() {
        assertThatThrownBy(() -> new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), "sideways"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CONTINUOUS or FRESH");
    }

    @Test
    void anExhaustedSpecificationRefinementFreezesTheBestDraftNotTheLatest() {
        // Refinement is not monotonic: a later draft can review worse, so freezing the latest would hand every downstream stage the weaker contract.
        String draftA = VALID_SPEC_DOCUMENT + "\n<!-- draft A -->\n";
        String draftB = VALID_SPEC_DOCUMENT + "\n<!-- draft B -->\n";
        String draftC = VALID_SPEC_DOCUMENT + "\n<!-- draft C -->\n";
        String draftD = VALID_SPEC_DOCUMENT + "\n<!-- draft D -->\n";
        sandbox.specMarkdown = draftA;
        SpecFidelityCriticService reviewer = mock(SpecFidelityCriticService.class);
        Answer<SpecFidelityCriticService.SpecificationReview> reviewCurrentSpecification = invocation -> {
            String current = sandbox.specMarkdown;
            if (current.equals(draftA)) {
                sandbox.specMarkdown = draftB;
                return new SpecFidelityCriticService.SpecificationReview(true, List.of("omission one", "omission two"));
            }
            if (current.equals(draftB)) {
                sandbox.specMarkdown = draftC;
                return new SpecFidelityCriticService.SpecificationReview(true, List.of("one remaining nit"));
            }
            sandbox.specMarkdown = draftD;
            return new SpecFidelityCriticService.SpecificationReview(true, List.of("regressed one", "regressed two", "regressed three"));
        };
        when(reviewer.reviewSpecification(anyString(), anyString(), any(), any())).thenAnswer(reviewCurrentSpecification);
        when(reviewer.reviewSpecification(anyString(), isNull(), anyString(), any(), any(), any())).thenAnswer(reviewCurrentSpecification);
        runner = new StagedGenerationRunner(agentLoopRunner, systemPromptService, stageCheckService, new AgentTranscriptWriter(""), approvedSpecs, reviewer, "FRESH");
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(1, "spec"), completed(1, "spec"), completed(1, "spec"),
                completed(1, "spec"), completed(3, "build"), completed(1, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));

        AgentLoopResult result = run(NEVER_CANCELLED, () -> SeededStructuralTests.EMPTY);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(approvedSpecs.approved("s")).as("the frozen contract is the best draft this concept reached, not the last one written").contains(draftB);
    }

    /** Records the substep every emitted progress line carried, which is what the client reads instead of parsing the "Phase 1/3: …" prose. */
    private static final class RecordingProgressSink implements GenerationProgressSink {

        private final GenerationActivityTracker tracker = new GenerationActivityTracker();

        private final List<String> steps = new java.util.ArrayList<>();

        @Override
        public void accept(String message) {
            steps.add(null);
        }

        @Override
        public void activity(String message, ExerciseGenerationActivityDTO activity) {
            steps.add(activity.step());
        }

        @Override
        public GenerationActivityTracker activityTracker() {
            return tracker;
        }
    }

    @Test
    void eachStage_publishesItsMachineReadableSubstepKey() {
        when(agentLoopRunner.run(anyString(), anyString(), any(), anyInt(), any(), any(), any())).thenReturn(completed(2, "spec"), completed(10, "build"),
                completed(3, "statement"));
        when(verifier.selfCheckTestsStage(any(), anyString(), eq(exercise), any(), any(SeededStructuralTests.class))).thenReturn(passingReport("testFoo"));
        RecordingProgressSink progress = new RecordingProgressSink();

        runner.run(exercise, baseTools, baseTools, "brief", Map.of(), sandbox, "s", NEVER_CANCELLED, null, progress, () -> SeededStructuralTests.EMPTY);

        // The keys are stable client contract, and they appear in stage order with no other vocabulary in between.
        assertThat(progress.steps).contains("spec", "artifacts", "statement");
        assertThat(progress.steps.stream().filter(java.util.Objects::nonNull).distinct().toList()).containsExactly("spec", "artifacts", "statement");
        assertThat(GenerationStage.SPEC.activityStep()).isEqualTo("spec");
        assertThat(GenerationStage.TESTS.activityStep()).isEqualTo("artifacts");
        assertThat(GenerationStage.STATEMENT.activityStep()).isEqualTo("statement");
    }
}
