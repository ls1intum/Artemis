package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * The quiz-exercise toolset for one agent round. One instance is created per round by
 * {@link QuizVariantAdapterService#createTools}; it is NOT a Spring bean.
 *
 * Questions are exchanged as the domain's own polymorphic JSON (the same format the quiz editor REST API
 * uses, discriminated by {@code "type": "multiple-choice" | "drag-and-drop" | "short-answer"}), so edits are
 * schema-checked simply by deserializing into the domain model. Validation errors are returned TO THE MODEL
 * as the tool result. DnD background/item images are carried over unchanged — image paths must not be added
 * or altered.
 *
 * Cancellation: once the cancel flag is set, every tool short-circuits with an instruction to stop and the
 * round's internal tool loop ends after that result; the pipeline performs the actual abort at the next round
 * boundary.
 */
class QuizVariantTools implements VariantToolset {

    private static final Logger log = LoggerFactory.getLogger(QuizVariantTools.class);

    /** Per-round tool-call budget (see {@link VariantRoundBudget}); lower than the programming budget — no repository work. */
    private static final int TOOL_CALL_BUDGET = 25;

    private final long quizExerciseId;

    private final String jobId;

    private final ExerciseVariantJobService jobService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizExerciseService quizExerciseService;

    private final ObjectMapper objectMapper;

    private volatile String finishSummary;

    private final ConcurrentHashMap<String, VariantJob.CallStat> toolCallStats = new ConcurrentHashMap<>();

    private final VariantRoundBudget budget;

    QuizVariantTools(long quizExerciseId, String jobId, ExerciseVariantJobService jobService, QuizExerciseRepository quizExerciseRepository,
            QuizExerciseService quizExerciseService, ObjectMapper objectMapper) {
        this.quizExerciseId = quizExerciseId;
        this.jobId = jobId;
        this.jobService = jobService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizExerciseService = quizExerciseService;
        this.objectMapper = objectMapper;
        this.budget = new VariantRoundBudget(TOOL_CALL_BUDGET, jobId, jobService);
    }

    @Override
    public List<ToolCallback> toolCallbacks() {
        return VariantToolset.withTiming(MethodToolCallbackProvider.builder().toolObjects(this).build().getToolCallbacks(), toolCallStats, budget::roundOver);
    }

    @Override
    public Map<String, VariantJob.CallStat> toolCallStats() {
        return Map.copyOf(toolCallStats);
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
            String error = replaceQuestion(quiz, index, questionJson);
            if (error != null) {
                return "Error: " + error;
            }
            quizExerciseService.save(quiz);
            log.debug("Variant job {}: replaced quiz question {} of exercise {}", jobId, index, quizExerciseId);
            return "Question " + index + " updated. " + (quiz.isValid() ? "The quiz is currently valid." : "The quiz is NOT valid yet — use validateQuiz for details.");
        }
        catch (Exception e) {
            return "Error: could not update question " + index + ": " + e.getMessage();
        }
    }

    /** One question replacement for the batch {@link #updateQuestions} tool: the target index and its new JSON. */
    public record QuestionEdit(@JsonPropertyDescription("the 0-based index of the question to replace (order of getQuestions)") Integer index,
            @JsonPropertyDescription("the full new question as JSON, in the same format getQuestions returns, including the \"type\" discriminator") String questionJson) {
    }

    @Tool(description = "Replace MULTIPLE questions of the variant quiz in a SINGLE call. Strongly prefer this over several updateQuestion calls when re-theming more than one "
            + "question: pass all the { index, questionJson } edits at once. Edits are applied in order and each is reported independently as updated or with a precise error; "
            + "a failed edit never blocks the others, and only the successful edits are persisted (once, at the end). Same rules as updateQuestion: keep each question's type and "
            + "element ids, and keep drag-and-drop image paths exactly as they are.")
    public String updateQuestions(@ToolParam(description = "the question replacements to apply; each has index and questionJson") List<QuestionEdit> edits) {
        String stop = stopNotice();
        if (stop != null) {
            return stop;
        }
        if (edits == null || edits.isEmpty()) {
            return "Error: no question edits were provided. Pass at least one { index, questionJson } edit.";
        }
        try {
            QuizExercise quiz = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
            StringBuilder report = new StringBuilder();
            int appliedCount = 0;
            for (int position = 0; position < edits.size(); position++) {
                QuestionEdit edit = edits.get(position);
                report.append("Edit ").append(position + 1);
                if (edit == null || edit.index() == null) {
                    report.append(": Error: the \"index\" argument is required — pass the 0-based question index from getQuestions.\n");
                    continue;
                }
                report.append(" (question ").append(edit.index()).append("): ");
                if (edit.questionJson() == null || edit.questionJson().isBlank()) {
                    report.append("Error: the \"questionJson\" argument is required — pass the full question JSON.\n");
                    continue;
                }
                String error = replaceQuestion(quiz, edit.index(), edit.questionJson());
                if (error != null) {
                    report.append("Error: ").append(error).append('\n');
                    continue;
                }
                appliedCount++;
                report.append("updated.\n");
            }
            if (appliedCount > 0) {
                quizExerciseService.save(quiz);
                log.debug("Variant job {}: replaced {} quiz question(s) of exercise {}", jobId, appliedCount, quizExerciseId);
            }
            report.append(appliedCount).append(" of ").append(edits.size()).append(" question(s) updated. ")
                    .append(quiz.isValid() ? "The quiz is currently valid." : "The quiz is NOT valid yet — use validateQuiz for details.");
            return report.toString();
        }
        catch (Exception e) {
            return "Error: could not update the questions: " + e.getMessage();
        }
    }

    /**
     * Replaces the question at {@code index} of {@code quiz} in memory with the deserialized {@code questionJson}
     * (id, statistic, and child back-references restored). Shared by the single {@link #updateQuestion} and the
     * batch {@link #updateQuestions} tools so the validation and reconnection rules are defined once; the caller
     * saves the quiz. Returns a precise, model-facing reason (WITHOUT an "Error: " prefix) on rejection, or
     * {@code null} when the replacement was applied.
     */
    private String replaceQuestion(QuizExercise quiz, int index, String questionJson) {
        List<QuizQuestion> questions = quiz.getQuizQuestions();
        if (index < 0 || index >= questions.size()) {
            return "question index " + index + " is out of range — the quiz has " + questions.size() + " question(s) (indices 0-" + (questions.size() - 1) + ").";
        }
        QuizQuestion existing = questions.get(index);
        if (existing == null) {
            return "there is no question at index " + index + " (the question list has a gap at this position).";
        }
        QuizQuestion updated;
        try {
            updated = objectMapper.readValue(questionJson, QuizQuestion.class);
        }
        catch (Exception parseError) {
            // Schema violations go back to the model as the tool result.
            return "the question JSON is invalid: " + parseError.getMessage() + " Use exactly the format getQuestions returns, including the \"type\" field.";
        }
        if (!existing.getClass().equals(updated.getClass())) {
            return "the question type must not change (existing question " + index + " is of type '" + existing.getClass().getSimpleName() + "').";
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
        // Grading metadata is invariant across a variant: the plan and the prompt both declare it, but a
        // replacement that changes points or scoring type still deserializes and still passes isValid(), and
        // QuizExerciseService.save recomputes the quiz maximum from the question points. Restore both from the
        // source question rather than trusting the model to have left them alone.
        updated.setPoints(existing.getPoints());
        updated.setScoringType(existing.getScoringType());
        if (!updated.isValid()) {
            return "the updated question is not valid (check: non-empty title/text, at least one correct multiple-choice option, "
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
        return null;
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
     * Enforces the image non-goal: the DnD background path must not change, and every drag item must keep the
     * picture path it was carried over with. An item may be dropped along with its picture, but a picture must
     * never be added, repointed, or moved to another item — the files belong to the source exercise.
     */
    private static String checkImagesUnchanged(QuizQuestion existing, QuizQuestion updated) {
        if (!(existing instanceof DragAndDropQuestion existingDnd) || !(updated instanceof DragAndDropQuestion updatedDnd)) {
            return null;
        }
        if (!Objects.equals(existingDnd.getBackgroundFilePath(), updatedDnd.getBackgroundFilePath())) {
            return "the background image path of a drag-and-drop question must not change (keep \"" + existingDnd.getBackgroundFilePath() + "\").";
        }
        Map<Long, String> existingPathsByItemId = picturePathsByItemId(existingDnd);
        for (DragItem updatedItem : updatedDnd.getDragItems() == null ? List.<DragItem>of() : updatedDnd.getDragItems()) {
            if (updatedItem == null) {
                return "the drag item list must not contain null entries — send every drag item you keep as a full object.";
            }
            // Identity, not membership: a path that merely still EXISTS somewhere may have been moved to another
            // item or attached to a newly invented one. A retained item that drops its path to null is the same
            // kind of change — removal is only permitted together with the item itself.
            if (!Objects.equals(existingPathsByItemId.get(updatedItem.getId()), updatedItem.getPictureFilePath())) {
                return "each drag item must keep its own image path — images must not be added, changed, moved between items, or removed from an item you keep. "
                        + "Only text and mappings may be edited. Existing picture paths per drag item id: " + existingPathsByItemId;
            }
        }
        return null;
    }

    private static Map<Long, String> picturePathsByItemId(DragAndDropQuestion question) {
        if (question.getDragItems() == null) {
            return Map.of();
        }
        Map<Long, String> pathsByItemId = new HashMap<>();
        question.getDragItems().stream().filter(item -> item.getId() != null && item.getPictureFilePath() != null)
                .forEach(item -> pathsByItemId.put(item.getId(), item.getPictureFilePath()));
        return pathsByItemId;
    }

    /**
     * Restores the @JsonIgnore'd child-to-parent pointer of one deserialized question so it is written correctly on
     * save. Answer options / drop locations / drag items / correct mappings / spots / solutions no longer carry a
     * back-reference (they are stored id-based inside the question's {@code content} JSON column, see
     * {@code QuizConfiguration.reconnectJSONIgnoreAttributes}); only the question's own statistic and its parent
     * exercise still need reconnecting.
     */
    private static void reconnectReplacedQuestion(QuizExercise quiz, QuizQuestion question) {
        question.setExercise(quiz);
        if (question.getQuizQuestionStatistic() != null) {
            question.getQuizQuestionStatistic().setQuestion(question);
        }
        switch (question) {
            case ShortAnswerQuestion saQuestion -> reconnectShortAnswerMappings(saQuestion);
            default -> {
            }
        }
    }

    /**
     * Rebuilds the correct mappings of a replaced short-answer question the way the quiz editor does.
     * <p>
     * Two things are wrong with the mappings as they arrive from the model, and both break the save:
     * <ul>
     * <li>Each mapping carries the <em>id</em> it was serialized with. The editor's supported path
     * ({@code ShortAnswerQuestionFromEditorDTO.toDomainObject}) never round-trips a mapping id — it always
     * builds {@code new ShortAnswerMapping()} and lets {@code orphanRemoval} delete the superseded rows.
     * Saving a mapping that has an id hands Hibernate a detached entity ("Detached entity passed to
     * persist", and once the row has moved on, an optimistic-lock failure).</li>
     * <li>The mapping's spot and solution arrive nested, so Jackson deserializes each as a separate
     * instance rather than the one held in the question's own spots/solutions collections.</li>
     * </ul>
     * Spot and solution ids are kept, exactly as the editor keeps them; only the mapping is recreated.
     * <p>
     * A mapping whose spot or solution cannot be resolved is dropped rather than persisted: the quiz then
     * fails {@code isValid()} with "spot has no mapped solution", which routes the problem back to the
     * agent as repair feedback instead of aborting the save. The editor rejects the whole request there,
     * but here the agent is mid-repair and a specific, recoverable complaint is worth more than a 400.
     */
    private static void reconnectShortAnswerMappings(ShortAnswerQuestion question) {
        if (question.getCorrectMappings() == null || question.getCorrectMappings().isEmpty()) {
            return;
        }
        List<ShortAnswerSpot> spots = question.getSpots() == null ? List.of() : question.getSpots();
        List<ShortAnswerSolution> solutions = question.getSolutions() == null ? List.of() : question.getSolutions();

        Set<ShortAnswerMapping> rebuilt = new HashSet<>();
        for (ShortAnswerMapping incoming : question.getCorrectMappings()) {
            ShortAnswerSpot spot = findSpot(spots, incoming.getSpot());
            ShortAnswerSolution solution = findSolution(solutions, incoming.getSolution());
            if (spot == null || solution == null) {
                log.debug("Dropping a short-answer mapping whose spot or solution is not part of the replaced question");
                continue;
            }
            // A fresh instance, never the incoming one: carrying the model-echoed id over is exactly what
            // makes the save fail. orphanRemoval deletes the superseded rows.
            ShortAnswerMapping mapping = new ShortAnswerMapping();
            mapping.setSpot(spot);
            mapping.setSolution(solution);
            mapping.setInvalid(incoming.isInvalid());
            rebuilt.add(mapping);
        }
        question.setCorrectMappings(rebuilt);
    }

    /** Matches by spot number first — the model may drop ids, but a spot without a number is meaningless. */
    private static ShortAnswerSpot findSpot(List<ShortAnswerSpot> spots, ShortAnswerSpot target) {
        if (target == null) {
            return null;
        }
        return spots.stream().filter(spot -> Objects.equals(spot.getSpotNr(), target.getSpotNr())).findFirst()
                .or(() -> spots.stream().filter(spot -> target.getId() != null && Objects.equals(spot.getId(), target.getId())).findFirst()).orElse(null);
    }

    /** Matches by id first, falling back to text for a solution the model added without one. */
    private static ShortAnswerSolution findSolution(List<ShortAnswerSolution> solutions, ShortAnswerSolution target) {
        if (target == null) {
            return null;
        }
        return solutions.stream().filter(solution -> target.getId() != null && Objects.equals(solution.getId(), target.getId())).findFirst()
                .or(() -> solutions.stream().filter(solution -> Objects.equals(solution.getText(), target.getText())).findFirst()).orElse(null);
    }

    /** Cancellation / tool-budget check every tool except finish short-circuits on; see {@link VariantRoundBudget}. */
    private String stopNotice() {
        return budget.stopNotice();
    }
}
