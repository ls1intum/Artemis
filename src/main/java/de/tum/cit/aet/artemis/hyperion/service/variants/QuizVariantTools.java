package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionComponent;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * The quiz-exercise toolset for one agent round. One instance is created per round by
 * {@link QuizVariantAdapters#createTools}; it is NOT a Spring bean.
 *
 * Questions are exchanged as the domain's own polymorphic JSON (the same format the quiz editor REST API
 * uses, discriminated by {@code "type": "multiple-choice" | "drag-and-drop" | "short-answer"}), so edits are
 * schema-checked simply by deserializing into the domain model. Validation errors are returned TO THE MODEL
 * as the tool result. DnD background/item images are carried over unchanged — image paths must not be added
 * or altered.
 *
 * Cancellation: once the cancel flag is set, every tool short-circuits with an instruction to stop; the
 * pipeline performs the actual abort at the next round boundary.
 */
class QuizVariantTools implements VariantToolset {

    private static final Logger log = LoggerFactory.getLogger(QuizVariantTools.class);

    /**
     * Per-round tool-call budget. Spring AI's internal tool loop has no iteration cap, and a model that
     * keeps re-reading and re-reasoning about the same questions would loop indefinitely (observed with a
     * local reasoning model: 100-message conversations). Once the budget is used up, every tool except
     * {@code finish} returns the same short directive to call finish, so runaway rounds converge.
     */
    private static final int TOOL_CALL_BUDGET = 25;

    private final long quizExerciseId;

    private final String jobId;

    private final ExerciseVariantJobService jobService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizExerciseService quizExerciseService;

    private final ObjectMapper objectMapper;

    private String finishSummary;

    private int toolCallsUsed;

    QuizVariantTools(long quizExerciseId, String jobId, ExerciseVariantJobService jobService, QuizExerciseRepository quizExerciseRepository,
            QuizExerciseService quizExerciseService, ObjectMapper objectMapper) {
        this.quizExerciseId = quizExerciseId;
        this.jobId = jobId;
        this.jobService = jobService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizExerciseService = quizExerciseService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ToolCallback> toolCallbacks() {
        return Arrays.asList(MethodToolCallbackProvider.builder().toolObjects(this).build().getToolCallbacks());
    }

    @Override
    public String finishSummary() {
        return finishSummary;
    }

    @Tool(description = "Get all questions of the variant quiz as a JSON array (the quiz editor format, discriminated by \"type\"). "
            + "Array order is the question order; use the array index with updateQuestion. Preserve the ids of elements you keep.")
    public String getQuestions() {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        try {
            QuizExercise quiz = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExerciseId);
            return serializeQuestions(objectMapper, quiz.getQuizQuestions());
        }
        catch (Exception e) {
            return "Error: could not read the quiz questions: " + e.getMessage();
        }
    }

    @Tool(description = "Replace one question of the variant quiz with the given question JSON (same format as getQuestions returns, "
            + "including the \"type\" discriminator). The question type must stay the same. Keep the ids of all elements you do not remove. "
            + "For drag-and-drop questions, image paths (background and drag item pictures) must remain exactly as they are — only text and mappings may change.")
    public String updateQuestion(@ToolParam(description = "the 0-based index of the question to replace (order of getQuestions)") Integer index,
            @ToolParam(description = "the full new question as JSON, in the same format getQuestions returns") String questionJson) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        // Boxed parameter on purpose: with a primitive int, a model omitting "index" crashes argument
        // unboxing inside Spring AI and kills the whole round; a null must go back to the model instead.
        if (index == null) {
            return "Error: the \"index\" argument is required — pass the 0-based question index from getQuestions.";
        }
        if (questionJson == null || questionJson.isBlank()) {
            return "Error: the \"questionJson\" argument is required — pass the full question JSON.";
        }
        try {
            // Statistics MUST be fetched eagerly: QuizService.save re-initializes/fixes the per-question
            // statistic objects, and a lazy statistic proxy on this detached instance would throw a
            // LazyInitializationException on save.
            QuizExercise quiz = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
            List<QuizQuestion> questions = quiz.getQuizQuestions();
            if (index < 0 || index >= questions.size()) {
                return "Error: question index " + index + " is out of range — the quiz has " + questions.size() + " question(s) (indices 0-" + (questions.size() - 1) + ").";
            }
            QuizQuestion existing = questions.get(index);
            if (existing == null) {
                return "Error: there is no question at index " + index + " (the question list has a gap at this position).";
            }
            QuizQuestion updated;
            try {
                updated = objectMapper.readValue(questionJson, QuizQuestion.class);
            }
            catch (Exception parseError) {
                // Schema violations go back to the model as the tool result.
                return "Error: the question JSON is invalid: " + parseError.getMessage() + " Use exactly the format getQuestions returns, including the \"type\" field.";
            }
            if (!existing.getClass().equals(updated.getClass())) {
                return "Error: the question type must not change (existing question " + index + " is of type '" + existing.getClass().getSimpleName() + "').";
            }
            String imageError = checkImagesUnchanged(existing, updated);
            if (imageError != null) {
                return imageError;
            }
            updated.setId(existing.getId());
            // Keep the persisted statistic: the JSON payload has none, and QuizService.save would otherwise
            // create a second statistic for the same question. The save path reconciles the statistic's
            // counters with the (possibly changed) options/spots, same as the quiz editor's update flow.
            updated.setQuizQuestionStatistic(existing.getQuizQuestionStatistic());
            if (!updated.isValid()) {
                return "Error: the updated question is not valid (check: non-empty title/text, at least one correct multiple-choice option, "
                        + "consistent drag-and-drop/short-answer mappings, valid scoring type). Fix the question and try again.";
            }
            questions.set(index, updated);
            // The child side owns every FK here (question -> exercise, option/mapping -> question, statistic ->
            // question), and all those back-references are @JsonIgnore'd, so they are null on the deserialized
            // instance. Saving without restoring them writes NULL FKs: the question row is orphaned and the
            // @OrderColumn list comes back with a null gap (observed as an NPE in VERIFYING). Same wiring as
            // QuizConfiguration.reconnectJSONIgnoreAttributes, but only for the replaced question — the full
            // reconnect walks lazy statistic-counter collections this detached graph has not fetched.
            reconnectReplacedQuestion(quiz, updated);
            quizExerciseService.save(quiz);
            log.debug("Variant job {}: replaced quiz question {} of exercise {}", jobId, index, quizExerciseId);
            return "Question " + index + " updated. " + (quiz.isValid() ? "The quiz is currently valid." : "The quiz is NOT valid yet — use validateQuiz for details.");
        }
        catch (Exception e) {
            return "Error: could not update question " + index + ": " + e.getMessage();
        }
    }

    @Tool(description = "Validate the variant quiz: overall validity and a per-question report. Fix all reported problems before you finish.")
    public String validateQuiz() {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        try {
            QuizExercise quiz = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExerciseId);
            return renderValidationReport(quiz);
        }
        catch (Exception e) {
            return "Error: could not validate the quiz: " + e.getMessage();
        }
    }

    // returnDirect ends the internal tool loop immediately — no extra LLM round after the model finishes,
    // and the "budget exhausted, call finish" directive has a guaranteed exit.
    @Tool(returnDirect = true, description = "Signal that you are done with this round and provide a short summary of what you changed and verified.")
    public String finish(@ToolParam(description = "a short summary of the changes made in this round") String summary) {
        this.finishSummary = summary;
        return "Summary recorded. You are done with this round.";
    }

    /**
     * Serializes questions with the DECLARED {@code List<QuizQuestion>} type: {@code writeValueAsString}'s
     * erased runtime type makes Jackson skip the {@code @JsonTypeInfo} discriminator, so the emitted JSON
     * would lack exactly the {@code "type"} field {@link #updateQuestion} requires the model to echo back.
     */
    static String serializeQuestions(ObjectMapper objectMapper, List<QuizQuestion> questions) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().forType(new TypeReference<List<QuizQuestion>>() {
        }).writeValueAsString(questions);
    }

    /**
     * Renders the same per-question validity report the verifier uses, so the agent can self-check with
     * exactly the signal it will be gated on.
     */
    static String renderValidationReport(QuizExercise quiz) {
        StringBuilder report = new StringBuilder();
        List<QuizQuestion> questions = quiz.getQuizQuestions();
        for (int i = 0; i < questions.size(); i++) {
            QuizQuestion question = questions.get(i);
            if (question == null) {
                // A null element means the @OrderColumn list has a gap (a question row lost its exercise FK).
                report.append("Question ").append(i).append(": MISSING — the question list has a gap at this position.\n");
                continue;
            }
            report.append("Question ").append(i).append(" (").append(question.getClass().getSimpleName()).append(", \"").append(question.getTitle()).append("\"): ")
                    .append(question.isValid() ? "valid" : "INVALID").append('\n');
        }
        report.append(quiz.isValid() ? "The quiz is valid." : "The quiz is NOT valid overall (every question must be valid; title and duration must be set).");
        return report.toString();
    }

    /**
     * Enforces the image non-goal: DnD background and drag-item picture paths must be exactly the carried-over
     * ones — no additions, removals, or changes.
     */
    private static String checkImagesUnchanged(QuizQuestion existing, QuizQuestion updated) {
        if (!(existing instanceof DragAndDropQuestion existingDnd) || !(updated instanceof DragAndDropQuestion updatedDnd)) {
            return null;
        }
        if (!Objects.equals(existingDnd.getBackgroundFilePath(), updatedDnd.getBackgroundFilePath())) {
            return "Error: the background image path of a drag-and-drop question must not change (keep \"" + existingDnd.getBackgroundFilePath() + "\").";
        }
        Set<String> existingPicturePaths = picturePaths(existingDnd);
        Set<String> updatedPicturePaths = picturePaths(updatedDnd);
        if (!existingPicturePaths.containsAll(updatedPicturePaths)) {
            return "Error: drag item image paths must not be added or changed — only text and mappings may be edited. Existing picture paths: " + existingPicturePaths;
        }
        return null;
    }

    private static Set<String> picturePaths(DragAndDropQuestion question) {
        if (question.getDragItems() == null) {
            return Set.of();
        }
        return new HashSet<>(question.getDragItems().stream().map(DragItem::getPictureFilePath).filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    /**
     * Restores the @JsonIgnore'd child-to-parent pointers of one deserialized question so the child-owned
     * FKs are written correctly on save — the per-question subset of
     * {@code QuizConfiguration.reconnectJSONIgnoreAttributes}.
     */
    private static void reconnectReplacedQuestion(QuizExercise quiz, QuizQuestion question) {
        question.setExercise(quiz);
        if (question.getQuizQuestionStatistic() != null) {
            question.getQuizQuestionStatistic().setQuestion(question);
        }
        switch (question) {
            case MultipleChoiceQuestion mcQuestion -> reconnectComponents(mcQuestion.getAnswerOptions(), mcQuestion);
            case DragAndDropQuestion dndQuestion -> {
                reconnectComponents(dndQuestion.getDropLocations(), dndQuestion);
                reconnectComponents(dndQuestion.getDragItems(), dndQuestion);
                reconnectComponents(dndQuestion.getCorrectMappings(), dndQuestion);
            }
            case ShortAnswerQuestion saQuestion -> {
                reconnectComponents(saQuestion.getSpots(), saQuestion);
                reconnectComponents(saQuestion.getSolutions(), saQuestion);
                reconnectComponents(saQuestion.getCorrectMappings(), saQuestion);
            }
            default -> {
            }
        }
    }

    private static <Q extends QuizQuestion> void reconnectComponents(Collection<? extends QuizQuestionComponent<Q>> components, Q question) {
        if (components != null) {
            components.forEach(component -> component.setQuestion(question));
        }
    }

    /**
     * Combined stop check for cancellation and the per-round tool budget — every tool except finish
     * short-circuits with the returned directive.
     */
    private String stopNotice() {
        if (jobService.isCancelRequested(jobId)) {
            return "The variant generation job was CANCELLED. Do not call any more tools; the round is over and all further work will be discarded.";
        }
        toolCallsUsed++;
        if (toolCallsUsed > TOOL_CALL_BUDGET) {
            return "TOOL BUDGET EXHAUSTED for this round (" + TOOL_CALL_BUDGET + " calls). Do not call any other tool. Call finish NOW with a short summary of what you changed.";
        }
        return null;
    }
}
