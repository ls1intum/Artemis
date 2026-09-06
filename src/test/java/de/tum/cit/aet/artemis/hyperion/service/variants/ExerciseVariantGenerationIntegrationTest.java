package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantJobDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantJobDetailDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

/**
 * Integration tests for the variant-generation pipeline: real Spring context, real database, real distributed data provider
 * job map, real quiz adapters/toolset — only the
 * {@code ChatModel} behind Hyperion's {@code ChatClient} is mocked (the established Hyperion test pattern,
 * see {@code HyperionQuizQuestionGenerationResourceTest}).
 *
 * The agent loop hands its toolset to Spring AI, whose internal tool-execution loop never runs against a
 * fully mocked model. The mock therefore drives the tools itself: the scripted {@code call(Prompt)} answer
 * pulls the {@link ToolCallback}s off the prompt's {@link ToolCallingChatOptions} and invokes them with
 * canned arguments — so the REAL tool implementations run against the REAL provisioned variant.
 *
 * The quiz pipeline is the vehicle for all pipeline-level tests; the programming pipeline's CI-backed
 * verify path (VERIFYING's build gate, collision retry) needs real local CI builds and is covered manually
 * and via the E2E suite instead.
 */
class ExerciseVariantGenerationIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "exvariantgen";

    private static final String EDITOR_LOGIN = TEST_PREFIX + "editor1";

    private static final String PLANNED_TITLE = "Cargo Bay Inventory Quiz";

    private static final String REWRITTEN_QUESTION_TITLE = "Cargo bay manifest check";

    private static final double TAMPERED_POINTS = 99;

    private static final ScoringType TAMPERED_SCORING_TYPE = ScoringType.PROPORTIONAL_WITHOUT_PENALTY;

    private static final String PLAN_JSON = """
            {
              "variantTitle": "%s",
              "problemStatement": "Check the cargo bay inventory of the space station.",
              "intendedChanges": ["Re-theme question 0 from generic knowledge to cargo bay inventory"],
              "invariants": ["Keep scoring types and points of all questions"]
            }
            """.formatted(PLANNED_TITLE);

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExerciseVariantJobService jobService;

    @Autowired
    private DistributedDataProvider distributedDataProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private Course course;

    private QuizExercise sourceQuiz;

    /** Raw results of the scripted tool calls — assertion failures show the real tool-level error. */
    private final List<String> toolTranscript = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setupTestData() {
        // The base class stubs the mocked model's options as plain ChatOptions — but tool callbacks only
        // survive into the Prompt when options.mutate() yields a ToolCallingChatOptions.Builder (see
        // DefaultChatClientUtils), as it does for every real chat model. Re-stub AFTER the base @BeforeEach.
        when(azureOpenAiChatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 2, 1);
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        sourceQuiz = createMcSaQuiz(course);
        toolTranscript.clear();
    }

    @AfterEach
    void resetChatModelMock() {
        reset(azureOpenAiChatModel);
    }

    /**
     * MC + SA questions only: the factory's drag-and-drop question references image files that do not exist
     * on disk, which would fail {@code validateQuizExerciseFiles} in VERIFYING and the DnD file copy during
     * provisioning — DnD coverage belongs to the E2E tests where real files exist.
     */
    private QuizExercise createMcSaQuiz(Course course) {
        QuizExercise quiz = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), QuizMode.INDIVIDUAL, course);
        quiz.addQuestion(QuizExerciseFactory.createMultipleChoiceQuestion());
        quiz.addQuestion(QuizExerciseFactory.createShortAnswerQuestion());
        quiz.setMaxPoints(quiz.getOverallQuizPoints());
        return quizExerciseRepository.save(quiz);
    }

    /** Two multiple-choice questions so the batch-replace test edits several questions of the same type in one call. */
    private QuizExercise createTwoMcQuiz(Course course) {
        QuizExercise quiz = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), QuizMode.INDIVIDUAL, course);
        quiz.addQuestion(QuizExerciseFactory.createMultipleChoiceQuestion());
        quiz.addQuestion(QuizExerciseFactory.createMultipleChoiceQuestion());
        quiz.setMaxPoints(quiz.getOverallQuizPoints());
        return quizExerciseRepository.save(quiz);
    }

    // --- Scripted ChatModel -----------------------------------------------------------------------------

    private record ScriptedModel(AtomicInteger planningCalls, AtomicInteger agentRounds, AtomicInteger critiqueCalls, AtomicInteger failureSummaryCalls) {

        ScriptedModel() {
            this(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
        }
    }

    /**
     * Installs the scripted answer for every LLM call of a run. Calls are classified exactly the way the
     * pipeline builds them: agent rounds carry tool callbacks, all other calls are told apart by their fixed
     * user-message text.
     *
     * @param planningResponse the raw planner output (valid or deliberately malformed)
     * @param agentBehavior    invoked with the round's tool callbacks; returns the round's final text
     * @param critiqueFindings findings the quiz critique soft gate reports on every verification pass
     */
    private ScriptedModel scriptChatModel(String planningResponse, Function<List<ToolCallback>, String> agentBehavior, List<String> critiqueFindings) {
        return scriptChatModel(planningResponse, agentBehavior, critiqueFindings, () -> {
        });
    }

    /**
     * Variant of {@link #scriptChatModel(String, Function, List)} with a hook that runs when the quiz critique
     * soft gate is called — the only point inside VERIFYING a test can act on.
     *
     * @param planningResponse the raw planner output
     * @param agentBehavior    invoked with the round's tool callbacks; returns the round's final text
     * @param critiqueFindings findings the critique reports on every verification pass
     * @param onCritique       runs before the critique answers, i.e. while the pipeline is in VERIFYING
     */
    private ScriptedModel scriptChatModel(String planningResponse, Function<List<ToolCallback>, String> agentBehavior, List<String> critiqueFindings, Runnable onCritique) {
        ScriptedModel script = new ScriptedModel();
        doAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            List<ToolCallback> tools = toolCallbacksOf(prompt);
            if (!tools.isEmpty()) {
                script.agentRounds().incrementAndGet();
                return textResponse(agentBehavior.apply(tools));
            }
            String userText = lastUserMessage(prompt);
            if (userText.contains("Produce the change plan")) {
                script.planningCalls().incrementAndGet();
                // Usage metadata only here: asserts the token accounting path without inflating other calls.
                return textResponseWithUsage(planningResponse);
            }
            if (userText.contains("Review the variant quiz")) {
                script.critiqueCalls().incrementAndGet();
                onCritique.run();
                ObjectNode critique = objectMapper.createObjectNode();
                ArrayNode findings = critique.putArray("findings");
                critiqueFindings.forEach(findings::add);
                return textResponse(critique.toString());
            }
            if (userText.contains("Write the summary for the instructor")) {
                script.failureSummaryCalls().incrementAndGet();
                return textResponse("AI post-mortem: the source exercise is untouched; retry with a simpler request.");
            }
            return textResponse("ok");
        }).when(azureOpenAiChatModel).call(any(Prompt.class));
        return script;
    }

    private static List<ToolCallback> toolCallbacksOf(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolOptions && toolOptions.getToolCallbacks() != null) {
            return toolOptions.getToolCallbacks();
        }
        return List.of();
    }

    private static String lastUserMessage(Prompt prompt) {
        return prompt.getInstructions().stream().filter(UserMessage.class::isInstance).reduce((first, second) -> second).map(message -> ((UserMessage) message).getText())
                .orElse("");
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse textResponseWithUsage(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), ChatResponseMetadata.builder().usage(new DefaultUsage(100, 20)).build());
    }

    /**
     * A round that re-titles question 0 like the happy path but ALSO rewrites its grading metadata. The prompt and
     * the plan both declare points and scoring type invariant, so the tool must restore them from the source.
     */
    private String applyGradingTamperEdit(List<ToolCallback> tools) {
        try {
            String questionsJson = callTool(tools, "getQuestions", objectMapper.createObjectNode().toString());
            ArrayNode questions = (ArrayNode) objectMapper.readTree(questionsJson);
            ObjectNode question = (ObjectNode) questions.get(0);
            question.put("title", REWRITTEN_QUESTION_TITLE);
            question.put("points", TAMPERED_POINTS);
            question.put("scoringType", TAMPERED_SCORING_TYPE.name());
            ObjectNode updateArguments = objectMapper.createObjectNode().put("index", 0).put("questionJson", question.toString());
            toolTranscript.add("updateQuestion: " + callTool(tools, "updateQuestion", updateArguments.toString()));
            callTool(tools, "finish", objectMapper.createObjectNode().put("summary", "Re-themed question 0 to the cargo bay domain").toString());
            return "done";
        }
        catch (Exception e) {
            toolTranscript.add("agent scripting failed: " + e);
            return "agent scripting failed: " + e.getMessage();
        }
    }

    /**
     * The canned happy-path agent round: read the questions through the real getQuestions tool, re-title
     * question 0, write it back through the real updateQuestion tool, and finish.
     */
    private String applyRetitleEdit(List<ToolCallback> tools) {
        try {
            String questionsJson = callTool(tools, "getQuestions", objectMapper.createObjectNode().toString());
            ArrayNode questions = (ArrayNode) objectMapper.readTree(questionsJson);
            ObjectNode question = (ObjectNode) questions.get(0);
            question.put("title", REWRITTEN_QUESTION_TITLE);
            ObjectNode updateArguments = objectMapper.createObjectNode().put("index", 0).put("questionJson", question.toString());
            String updateResult = callTool(tools, "updateQuestion", updateArguments.toString());
            toolTranscript.add("updateQuestion: " + updateResult);
            callTool(tools, "finish", objectMapper.createObjectNode().put("summary", "Re-themed question 0 to the cargo bay domain").toString());
            return "done";
        }
        catch (Exception e) {
            toolTranscript.add("agent scripting failed: " + e);
            return "agent scripting failed: " + e.getMessage();
        }
    }

    /**
     * A batch agent round: read all questions, re-title EVERY question, and write them back through the real
     * updateQuestions tool in a single call (performance lever A1, quiz side).
     */
    private String applyBatchRetitleEdit(List<ToolCallback> tools) {
        try {
            String questionsJson = callTool(tools, "getQuestions", objectMapper.createObjectNode().toString());
            ArrayNode questions = (ArrayNode) objectMapper.readTree(questionsJson);
            ArrayNode edits = objectMapper.createArrayNode();
            for (int index = 0; index < questions.size(); index++) {
                ObjectNode question = (ObjectNode) questions.get(index);
                question.put("title", REWRITTEN_QUESTION_TITLE + " " + index);
                edits.add(objectMapper.createObjectNode().put("index", index).put("questionJson", question.toString()));
            }
            String updateResult = callTool(tools, "updateQuestions", objectMapper.createObjectNode().set("edits", edits).toString());
            toolTranscript.add("updateQuestions: " + updateResult);
            callTool(tools, "finish", objectMapper.createObjectNode().put("summary", "Re-themed all questions to the cargo bay domain in one batch").toString());
            return "done";
        }
        catch (Exception e) {
            toolTranscript.add("agent scripting failed: " + e);
            return "agent scripting failed: " + e.getMessage();
        }
    }

    private static String callTool(List<ToolCallback> tools, String name, String jsonArguments) {
        ToolCallback tool = tools.stream().filter(callback -> callback.getToolDefinition().name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Tool " + name + " is not part of the round's toolset"));
        return tool.call(jsonArguments);
    }

    // --- Helpers ----------------------------------------------------------------------------------------

    private static VariantGenerationRequestDTO domainChangeRequest(VariantPlacementDTO placement) {
        return new VariantGenerationRequestDTO(null, "space station cargo bay", null, null, placement);
    }

    private static VariantPlacementDTO standalonePlacement() {
        return new VariantPlacementDTO(VariantPlacementDTO.PlacementType.STANDALONE, null, null);
    }

    private String startJob(long exerciseId, VariantGenerationRequestDTO requestDto) throws Exception {
        VariantJobStartDTO start = request.postWithResponseBody("/api/hyperion/exercises/" + exerciseId + "/generate-variant", requestDto, VariantJobStartDTO.class, HttpStatus.OK);
        assertThat(start.jobId()).isNotBlank();
        return start.jobId();
    }

    private VariantJob awaitTerminal(String jobId, String login) {
        await().atMost(Duration.ofSeconds(60)).until(() -> jobService.getJob(jobId, login).map(job -> job.getPhase().isTerminal()).orElse(false));
        return jobService.getJob(jobId, login).orElseThrow();
    }

    // --- Happy path + job endpoints ---------------------------------------------------------------------

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldGenerateStandaloneQuizVariantEndToEnd() throws Exception {
        ScriptedModel script = scriptChatModel(PLAN_JSON, this::applyRetitleEdit, List.of());

        String jobId = startJob(sourceQuiz.getId(), domainChangeRequest(standalonePlacement()));
        VariantJob job = awaitTerminal(jobId, EDITOR_LOGIN);

        assertThat(job.getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        assertThat(job.getWarnings()).isEmpty();
        assertThat(job.getVariantExerciseTitle()).isEqualTo(PLANNED_TITLE);
        assertThat(script.planningCalls()).hasValue(1);
        assertThat(script.agentRounds()).hasValue(1);
        assertThat(script.critiqueCalls()).hasValue(1);
        // A clean run needs no post-mortem — the extra LLM call is reserved for failures and flagged drafts.
        assertThat(script.failureSummaryCalls()).hasValue(0);
        assertThat(job.getInstructorSummary()).isNull();
        // Planning reported usage metadata (100 + 20 tokens) — the accounting must land on the job record.
        assertThat(job.getTotalTokensUsed()).isGreaterThanOrEqualTo(120);

        // The clone is a real, valid quiz whose scripted edit landed; the source is untouched.
        // Tool results come back JSON-encoded from ToolCallback.call, hence contains() instead of startsWith().
        assertThat(toolTranscript).anySatisfy(entry -> assertThat(entry).contains("Question 0 updated"));
        assertThat(job.getVariantExerciseId()).isNotNull().isNotEqualTo(sourceQuiz.getId());
        QuizExercise variant = quizExerciseRepository.findByIdWithQuestionsElseThrow(job.getVariantExerciseId());
        assertThat(variant.getTitle()).isEqualTo(PLANNED_TITLE);
        assertThat(variant.isValid()).isTrue();
        assertThat(variant.getQuizQuestions().getFirst().getTitle()).isEqualTo(REWRITTEN_QUESTION_TITLE);
        QuizExercise source = quizExerciseRepository.findByIdWithQuestionsElseThrow(sourceQuiz.getId());
        assertThat(source.getQuizQuestions().getFirst().getTitle()).isNotEqualTo(REWRITTEN_QUESTION_TITLE);

        // Every pipeline phase recorded its step output (the modal's expandable panels).
        assertThat(job.getStepOutputs()).containsKeys(VariantJobPhase.ANALYZING, VariantJobPhase.PLANNING, VariantJobPhase.PROVISIONING, VariantJobPhase.TRANSFORMING,
                VariantJobPhase.VERIFYING);
        assertThat(job.getStepOutputs().get(VariantJobPhase.TRANSFORMING).getFirst().summary()).contains("Agent round 1");

        // Tray list + monitor-modal detail endpoints see the finished job incl. the original request.
        List<VariantJobDTO> jobs = request.getList("/api/hyperion/variant-jobs", HttpStatus.OK, VariantJobDTO.class);
        assertThat(jobs).anySatisfy(entry -> {
            assertThat(entry.jobId()).isEqualTo(jobId);
            assertThat(entry.variantExerciseTitle()).isEqualTo(PLANNED_TITLE);
        });
        VariantJobDetailDTO detail = request.get("/api/hyperion/variant-jobs/" + jobId, HttpStatus.OK, VariantJobDetailDTO.class);
        assertThat(detail.request().domainText()).isEqualTo("space station cargo bay");
        assertThat(detail.stepOutputs()).containsKey(VariantJobPhase.PLANNING);

        // Terminal jobs can no longer be cancelled.
        request.delete("/api/hyperion/variant-jobs/" + jobId, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldNotLetTheAgentChangeQuestionPointsOrScoringType() throws Exception {
        QuizQuestion sourceQuestion = quizExerciseRepository.findByIdWithQuestionsElseThrow(sourceQuiz.getId()).getQuizQuestions().getFirst();
        double sourcePoints = sourceQuestion.getPoints();
        ScoringType sourceScoringType = sourceQuestion.getScoringType();
        // The scripted round only proves anything if it really asks for different values.
        assertThat(sourcePoints).isNotEqualTo(TAMPERED_POINTS);
        assertThat(sourceScoringType).isNotEqualTo(TAMPERED_SCORING_TYPE);

        scriptChatModel(PLAN_JSON, this::applyGradingTamperEdit, List.of());

        String jobId = startJob(sourceQuiz.getId(), domainChangeRequest(standalonePlacement()));
        VariantJob job = awaitTerminal(jobId, EDITOR_LOGIN);

        assertThat(job.getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        QuizExercise variant = quizExerciseRepository.findByIdWithQuestionsElseThrow(job.getVariantExerciseId());
        QuizQuestion variantQuestion = variant.getQuizQuestions().getFirst();
        // The content change was applied ...
        assertThat(variantQuestion.getTitle()).isEqualTo(REWRITTEN_QUESTION_TITLE);
        // ... while the grading metadata is the source's, not what the model sent. Otherwise QuizExerciseService.save
        // would recompute the quiz maximum from the tampered points and the variant would grade differently.
        assertThat(variantQuestion.getPoints()).isEqualTo(sourcePoints);
        assertThat(variantQuestion.getScoringType()).isEqualTo(sourceScoringType);
        assertThat(variant.getMaxPoints()).isEqualTo(sourceQuiz.getMaxPoints());
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldReplaceMultipleQuizQuestionsInOneBatchCall() throws Exception {
        QuizExercise twoMcQuiz = createTwoMcQuiz(course);
        ScriptedModel script = scriptChatModel(PLAN_JSON, this::applyBatchRetitleEdit, List.of());

        String jobId = startJob(twoMcQuiz.getId(), domainChangeRequest(standalonePlacement()));
        VariantJob job = awaitTerminal(jobId, EDITOR_LOGIN);

        assertThat(job.getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        assertThat(script.agentRounds()).hasValue(1);
        // One batch call reported both replacements and the running total.
        assertThat(toolTranscript).anySatisfy(entry -> assertThat(entry).contains("2 of 2 question(s) updated"));

        // Both questions of the clone carry their new per-index title; the source is untouched.
        QuizExercise variant = quizExerciseRepository.findByIdWithQuestionsElseThrow(job.getVariantExerciseId());
        assertThat(variant.isValid()).isTrue();
        assertThat(variant.getQuizQuestions()).hasSize(2);
        assertThat(variant.getQuizQuestions().get(0).getTitle()).isEqualTo(REWRITTEN_QUESTION_TITLE + " 0");
        assertThat(variant.getQuizQuestions().get(1).getTitle()).isEqualTo(REWRITTEN_QUESTION_TITLE + " 1");
        QuizExercise source = quizExerciseRepository.findByIdWithQuestionsElseThrow(twoMcQuiz.getId());
        assertThat(source.getQuizQuestions().get(0).getTitle()).isNotEqualTo(REWRITTEN_QUESTION_TITLE + " 0");
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldRunTwoJobsForTheSameExerciseInParallel() throws Exception {
        scriptChatModel(PLAN_JSON, this::applyRetitleEdit, List.of());

        String firstJobId = startJob(sourceQuiz.getId(), domainChangeRequest(standalonePlacement()));
        String secondJobId = startJob(sourceQuiz.getId(), domainChangeRequest(standalonePlacement()));
        assertThat(firstJobId).isNotEqualTo(secondJobId);

        VariantJob firstJob = awaitTerminal(firstJobId, EDITOR_LOGIN);
        VariantJob secondJob = awaitTerminal(secondJobId, EDITOR_LOGIN);
        assertThat(firstJob.getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        assertThat(secondJob.getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        assertThat(firstJob.getVariantExerciseId()).isNotEqualTo(secondJob.getVariantExerciseId());
    }

    // --- Failure paths ------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldFailAfterRepromptsWhenPlannerOutputStaysMalformed() throws Exception {
        ScriptedModel script = scriptChatModel("this is not a change plan", tools -> "unused", List.of());

        String jobId = startJob(sourceQuiz.getId(), domainChangeRequest(standalonePlacement()));
        VariantJob job = awaitTerminal(jobId, EDITOR_LOGIN);

        assertThat(job.getPhase()).isEqualTo(VariantJobPhase.FAILED);
        assertThat(job.getFailedInPhase()).isEqualTo(VariantJobPhase.PLANNING);
        assertThat(job.getFailureDetail()).contains("PLANNING");
        // 1 initial + 2 re-prompts, then the failure-summary call.
        assertThat(script.planningCalls()).hasValue(3);
        assertThat(script.failureSummaryCalls()).hasValue(1);
        assertThat(job.getInstructorSummary()).contains("AI post-mortem");
        // Nothing was provisioned, so there is no clone to link or clean up.
        assertThat(job.getVariantExerciseId()).isNull();
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldKeepDraftWithWarningsWhenVerificationBudgetIsExhausted() throws Exception {
        // Agent rounds change nothing; the critique soft gate reports the same finding on every pass. The
        // finding carries a build-log section (like the programming build gate's findings): the warning list
        // must get the summarized form — logs cut at the marker — while the full text stays in the VERIFYING
        // step output for inspection.
        String buildLogs = "compiler output line\n".repeat(200);
        ScriptedModel script = scriptChatModel(PLAN_JSON, tools -> "no changes applied",
                List.of("The requested domain change was not applied" + VariantBuildVerificationService.BUILD_LOGS_SECTION + buildLogs));

        String jobId = startJob(sourceQuiz.getId(), domainChangeRequest(standalonePlacement()));
        VariantJob job = awaitTerminal(jobId, EDITOR_LOGIN);

        // Budget exhausted with red gates → flagged draft, never silent deletion.
        assertThat(job.getPhase()).isEqualTo(VariantJobPhase.DRAFT_WITH_WARNINGS);
        assertThat(job.getWarnings()).isNotEmpty().anySatisfy(warning -> assertThat(warning).contains("QUIZ_CRITIQUE").contains("The requested domain change was not applied")
                .contains("build logs omitted").doesNotContain("compiler output line"));
        // The verify history keeps ONE output per attempt, oldest first — earlier failures stay inspectable
        // instead of being overwritten by the latest message (debugging aid for the modal's step panels).
        List<StepOutput> verifyOutputs = job.getStepOutputs().get(VariantJobPhase.VERIFYING);
        assertThat(verifyOutputs).hasSize(5);
        assertThat(verifyOutputs.getFirst().summary()).contains("attempt 1/5");
        assertThat(verifyOutputs.getLast().summary()).contains("attempt 5/5");
        assertThat(verifyOutputs).allSatisfy(output -> assertThat(output.detail()).contains("compiler output line"));
        assertThat(script.agentRounds()).hasValue(5);
        assertThat(job.getStepOutputs()).containsKey(VariantJobPhase.REPAIRING);
        // The draft is kept for the instructor to repair in the editor.
        assertThat(job.getVariantExerciseId()).isNotNull();
        assertThat(quizExerciseRepository.findById(job.getVariantExerciseId())).isPresent();
        // A flagged draft carries the same "what happened & how to continue" post-mortem a failure does — the
        // raw gate warnings alone do not tell the instructor what to do next.
        assertThat(script.failureSummaryCalls()).hasValue(1);
        assertThat(job.getInstructorSummary()).contains("AI post-mortem");

        // Stuck-repair-loop detection: the SAME QUIZ_CRITIQUE finding recurs every round here, so the pipeline
        // must inject the escalation note starting from the round AFTER it has recurred twice (attempt 3's
        // prompt) — never on the first two, which have only seen the problem once/twice respectively by the
        // time each of THEIR prompts was built.
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(azureOpenAiChatModel, atLeast(5)).call(promptCaptor.capture());
        List<String> agentRoundUserMessages = promptCaptor.getAllValues().stream().filter(prompt -> !toolCallbacksOf(prompt).isEmpty())
                .map(ExerciseVariantGenerationIntegrationTest::lastUserMessage).toList();
        assertThat(agentRoundUserMessages).hasSize(5);
        assertThat(agentRoundUserMessages.get(0)).doesNotContain("Stuck-loop warning");
        assertThat(agentRoundUserMessages.get(1)).doesNotContain("Stuck-loop warning");
        assertThat(agentRoundUserMessages.subList(2, 5)).allSatisfy(message -> assertThat(message).contains("Stuck-loop warning"));
    }

    // --- Cooperative cancellation -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldHonorCancellationAtThePhaseBoundaryAndDeleteTheClone() throws Exception {
        AtomicReference<Long> provisionedExerciseId = new AtomicReference<>();
        // Pick the job under test by its own id. Other tests in this class start jobs for the same login that
        // never reach a terminal phase, and the job map is shared and not cleared between tests, so "the first
        // non-terminal job of this user" could resolve to one of those and cancel the wrong job.
        AtomicReference<String> jobIdUnderTest = new AtomicReference<>();
        // The agent round sets the cancel flag mid-TRANSFORMING (through the same service the DELETE endpoint
        // uses); the pipeline must honor it at the next phase boundary — before VERIFYING.
        scriptChatModel(PLAN_JSON, tools -> {
            // The pipeline runs async, so the id may not be published yet when this round starts.
            await().atMost(Duration.ofSeconds(10)).until(() -> jobIdUnderTest.get() != null);
            VariantJob runningJob = jobService.getJob(jobIdUnderTest.get(), EDITOR_LOGIN).orElseThrow();
            provisionedExerciseId.set(runningJob.getVariantExerciseId());
            jobService.requestCancel(runningJob.getJobId(), EDITOR_LOGIN);
            return "round interrupted by cancellation";
        }, List.of());

        String jobId = startJob(sourceQuiz.getId(), domainChangeRequest(standalonePlacement()));
        jobIdUnderTest.set(jobId);
        VariantJob job = awaitTerminal(jobId, EDITOR_LOGIN);

        assertThat(job.getPhase()).isEqualTo(VariantJobPhase.CANCELLED);
        // The provisioned clone was deleted on the same cleanup path as hard failures.
        assertThat(job.getVariantExerciseId()).isNull();
        assertThat(provisionedExerciseId.get()).isNotNull();
        await().atMost(Duration.ofSeconds(30)).until(() -> quizExerciseRepository.findById(provisionedExerciseId.get()).isEmpty());
    }

    /**
     * VERIFYING is the longest phase of a round — for programming exercises it waits for real CI builds. A cancel
     * accepted while it runs used to be dropped whenever the report came back green: the loop returned straight
     * into FINALIZING, which is past the last cancel window, and the job completed anyway.
     */
    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldHonorCancellationRequestedDuringTheFinalVerification() throws Exception {
        AtomicReference<Long> provisionedExerciseId = new AtomicReference<>();
        AtomicReference<String> jobIdUnderTest = new AtomicReference<>();
        // Green gates, so without the post-verification check the job would go on to FINALIZING and COMPLETED.
        scriptChatModel(PLAN_JSON, this::applyRetitleEdit, List.of(), () -> {
            await().atMost(Duration.ofSeconds(10)).until(() -> jobIdUnderTest.get() != null);
            VariantJob runningJob = jobService.getJob(jobIdUnderTest.get(), EDITOR_LOGIN).orElseThrow();
            provisionedExerciseId.set(runningJob.getVariantExerciseId());
            jobService.requestCancel(runningJob.getJobId(), EDITOR_LOGIN);
        });

        String jobId = startJob(sourceQuiz.getId(), domainChangeRequest(standalonePlacement()));
        jobIdUnderTest.set(jobId);
        VariantJob job = awaitTerminal(jobId, EDITOR_LOGIN);

        assertThat(job.getPhase()).isEqualTo(VariantJobPhase.CANCELLED);
        assertThat(job.getVariantExerciseId()).isNull();
        assertThat(provisionedExerciseId.get()).isNotNull();
        await().atMost(Duration.ofSeconds(30)).until(() -> quizExerciseRepository.findById(provisionedExerciseId.get()).isEmpty());
    }

    // --- REST validation + per-user scoping ---------------------------------------------------------------

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldRejectInvalidGenerationRequests() throws Exception {
        // No intent on any dimension.
        request.postWithResponseBody("/api/hyperion/exercises/" + sourceQuiz.getId() + "/generate-variant",
                new VariantGenerationRequestDTO(null, null, null, null, standalonePlacement()), VariantJobStartDTO.class, HttpStatus.BAD_REQUEST);
        // SAME_EXAM_GROUP is only valid for exam exercises.
        request.postWithResponseBody("/api/hyperion/exercises/" + sourceQuiz.getId() + "/generate-variant",
                domainChangeRequest(new VariantPlacementDTO(VariantPlacementDTO.PlacementType.SAME_EXAM_GROUP, null, null)), VariantJobStartDTO.class, HttpStatus.BAD_REQUEST);
        // EXISTING_GROUP without a group id.
        request.postWithResponseBody("/api/hyperion/exercises/" + sourceQuiz.getId() + "/generate-variant",
                domainChangeRequest(new VariantPlacementDTO(VariantPlacementDTO.PlacementType.EXISTING_GROUP, null, null)), VariantJobStartDTO.class, HttpStatus.BAD_REQUEST);

        // NEW_GROUP payloads are validated too — @Valid cascades into the nested group DTO.
        request.postWithResponseBody("/api/hyperion/exercises/" + sourceQuiz.getId() + "/generate-variant", domainChangeRequest(
                new VariantPlacementDTO(VariantPlacementDTO.PlacementType.NEW_GROUP, null, new CreateExerciseVariantGroupDTO("a".repeat(256), 10.0, null, null, null, null, null))),
                VariantJobStartDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody("/api/hyperion/exercises/" + sourceQuiz.getId() + "/generate-variant",
                domainChangeRequest(new VariantPlacementDTO(VariantPlacementDTO.PlacementType.NEW_GROUP, null,
                        new CreateExerciseVariantGroupDTO("Cargo bay variants", -1.0, null, null, null, null, null))),
                VariantJobStartDTO.class, HttpStatus.BAD_REQUEST);

        // Unsupported exercise type (no text adapters registered).
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2),
                course);
        textExercise = exerciseRepository.save(textExercise);
        request.postWithResponseBody("/api/hyperion/exercises/" + textExercise.getId() + "/generate-variant", domainChangeRequest(standalonePlacement()), VariantJobStartDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor2", roles = "EDITOR")
    void shouldHideForeignJobsFromOtherUsers() throws Exception {
        // A job of editor1, created directly on the job map (no pipeline run needed for scoping checks).
        var initiator = userUtilService.getUserByLogin(EDITOR_LOGIN);
        VariantJob foreignJob = jobService.startJob(initiator, sourceQuiz, domainChangeRequest(standalonePlacement()));

        // Foreign job detail is 404 — indistinguishable from an unknown job so ids cannot be probed.
        request.get("/api/hyperion/variant-jobs/" + foreignJob.getJobId(), HttpStatus.NOT_FOUND, VariantJobDetailDTO.class);
        List<VariantJobDTO> jobs = request.getList("/api/hyperion/variant-jobs", HttpStatus.OK, VariantJobDTO.class);
        assertThat(jobs).noneSatisfy(entry -> assertThat(entry.jobId()).isEqualTo(foreignJob.getJobId()));
    }

    // --- Placement (NEW_GROUP + exam group) ---------------------------------------------------------------

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldPlaceVariantIntoNewVariantGroup() throws Exception {
        scriptChatModel(PLAN_JSON, this::applyRetitleEdit, List.of());
        VariantPlacementDTO placement = new VariantPlacementDTO(VariantPlacementDTO.PlacementType.NEW_GROUP, null,
                new CreateExerciseVariantGroupDTO("Cargo bay variants", 10.0, null, null, null, null, null));

        String jobId = startJob(sourceQuiz.getId(), domainChangeRequest(placement));
        VariantJob job = awaitTerminal(jobId, EDITOR_LOGIN);

        assertThat(job.getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        List<ExerciseVariantGroupDTO> groups = request.getList("/api/exercise/courses/" + course.getId() + "/exercise-variant-groups", HttpStatus.OK,
                ExerciseVariantGroupDTO.class);
        assertThat(groups).anySatisfy(group -> {
            assertThat(group.title()).isEqualTo("Cargo bay variants");
            assertThat(group.exerciseIds()).contains(job.getVariantExerciseId());
        });
    }

    /** NEW_GROUP promises to group the variant WITH its source, so a source that cannot join is rejected up front. */
    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldRejectNewGroupPlacementWhenTheSourceCannotJoinTheGroup() throws Exception {
        QuizExercise synchronizedQuiz = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), QuizMode.SYNCHRONIZED, course);
        synchronizedQuiz.addQuestion(QuizExerciseFactory.createMultipleChoiceQuestion());
        synchronizedQuiz.setMaxPoints(synchronizedQuiz.getOverallQuizPoints());
        synchronizedQuiz = quizExerciseRepository.save(synchronizedQuiz);
        VariantPlacementDTO placement = new VariantPlacementDTO(VariantPlacementDTO.PlacementType.NEW_GROUP, null,
                new CreateExerciseVariantGroupDTO("Cargo bay variants", 10.0, null, null, null, null, null));

        request.postWithResponseBody("/api/hyperion/exercises/" + synchronizedQuiz.getId() + "/generate-variant", domainChangeRequest(placement), VariantJobStartDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldPlaceExamVariantInTheSourcesExamExerciseGroup() throws Exception {
        Exam exam = ExamFactory.generateExam(course, ZonedDateTime.now().minusHours(2), ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), false);
        ExamFactory.generateExerciseGroup(true, exam);
        exam = examRepository.save(exam);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();
        QuizExercise examQuiz = QuizExerciseFactory.generateQuizExerciseForExam(exerciseGroup);
        examQuiz.addQuestion(QuizExerciseFactory.createMultipleChoiceQuestion());
        examQuiz.addQuestion(QuizExerciseFactory.createShortAnswerQuestion());
        examQuiz = quizExerciseRepository.save(examQuiz);
        scriptChatModel(PLAN_JSON, this::applyRetitleEdit, List.of());

        String jobId = startJob(examQuiz.getId(), domainChangeRequest(new VariantPlacementDTO(VariantPlacementDTO.PlacementType.SAME_EXAM_GROUP, null, null)));
        VariantJob job = awaitTerminal(jobId, EDITOR_LOGIN);

        assertThat(job.getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        QuizExercise variant = quizExerciseRepository.findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(job.getVariantExerciseId()).orElseThrow();
        assertThat(variant.isExamExercise()).isTrue();
        assertThat(variant.getExerciseGroup().getId()).isEqualTo(exerciseGroup.getId());
    }

    // --- Component sanity: DTO mapping (kept close to the pipeline tests) ----------------------------------

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldExposeStepOutputsAndRequestOnTheDetailDTO() {
        var initiator = userUtilService.getUserByLogin(EDITOR_LOGIN);
        VariantJob job = jobService.startJob(initiator, sourceQuiz, domainChangeRequest(standalonePlacement()));
        jobService.recordChangePlan(job.getJobId(), new ChangePlan(PLANNED_TITLE, "statement", List.of("change"), List.of("invariant")));
        jobService.recordStepOutput(job.getJobId(), VariantJobPhase.PLANNING, new StepOutput("summary", "detail", java.time.Instant.now()));
        // A second output for the same phase appends to the history instead of overwriting the first.
        jobService.recordStepOutput(job.getJobId(), VariantJobPhase.PLANNING, new StepOutput("summary 2", "detail 2", java.time.Instant.now()));

        VariantJobDetailDTO detail = VariantJobDetailDTO.of(jobService.getJob(job.getJobId(), EDITOR_LOGIN).orElseThrow());
        assertThat(detail.job().variantExerciseTitle()).isEqualTo(PLANNED_TITLE);
        assertThat(detail.request().placement().type()).isEqualTo(VariantPlacementDTO.PlacementType.STANDALONE);
        assertThat(detail.stepOutputs()).containsEntry(VariantJobPhase.PLANNING,
                List.of(new VariantJobDetailDTO.StepOutputDTO("summary", "detail"), new VariantJobDetailDTO.StepOutputDTO("summary 2", "detail 2")));
        assertThat(Map.copyOf(detail.stepOutputs())).hasSize(1);
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldMarkNonTerminalJobsWithoutHeartbeatAsFailedStaleOnRead() {
        var initiator = userUtilService.getUserByLogin(EDITOR_LOGIN);
        VariantJob job = jobService.startJob(initiator, sourceQuiz, domainChangeRequest(standalonePlacement()));
        // Simulate the worker node vanishing: push the heartbeat past the staleness threshold directly on the map.
        DistributedMap<String, VariantJob> jobMap = distributedDataProvider.getExpiringMap(ExerciseVariantJobService.JOB_MAP_NAME, Duration.ofHours(24));
        VariantJob stored = jobMap.get(job.getJobId());
        stored.setLastHeartbeatAt(Instant.now().minus(Duration.ofHours(1)));
        jobMap.put(job.getJobId(), stored);

        VariantJob reconciled = jobService.getJob(job.getJobId(), EDITOR_LOGIN).orElseThrow();
        assertThat(reconciled.getPhase()).isEqualTo(VariantJobPhase.FAILED);
        assertThat(reconciled.getFailedInPhase()).isEqualTo(VariantJobPhase.ANALYZING);
        assertThat(reconciled.getFailureDetail()).isNotBlank();
        assertThat(reconciled.getFinishedAt()).isNotNull();
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void shouldNotMarkFreshlyStartedJobsAsStale() {
        var initiator = userUtilService.getUserByLogin(EDITOR_LOGIN);
        VariantJob job = jobService.startJob(initiator, sourceQuiz, domainChangeRequest(standalonePlacement()));

        VariantJob read = jobService.getJob(job.getJobId(), EDITOR_LOGIN).orElseThrow();
        assertThat(read.getPhase()).isEqualTo(VariantJobPhase.ANALYZING);
    }
}
