package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.atlas.dto.FlavorStripEditsDTO;
import de.tum.cit.aet.artemis.atlas.dto.FlavorStripEditsDTO.EditDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Extracts learning-relevant content from {@link LearningObject}s (exercises and lecture units)
 * into {@link ExtractedContentDTO}s for downstream LLM consumption. Supports all exercise types
 * (programming, text, modeling, file upload, quiz); lecture unit types are not yet supported.
 * <p>
 * Text, modeling and file-upload exercises carry a prose problem statement (flavor-stripped) plus
 * their example/sample solution; quizzes have no problem statement, so their content is assembled
 * from the questions, answer options, and correct solutions. All extracted content is server-side
 * only; both the batch orchestration path and the on-demand {@code OrchestratorReadToolsService.getExerciseContent}
 * read tool sanitize it (fence-neutralized, truncated) via {@code CompetencyOrchestrationService.sanitizeForPrompt}
 * before it reaches the prompt.
 * <p>
 * To add a new learning object type:
 * <ol>
 * <li>Add a {@code case} branch to the pattern-matching {@code switch} in
 * {@link #extractContent(LearningObject, boolean)} for the new {@code LearningObject} subtype</li>
 * <li>Create a private {@code extractFrom*()} method accepting the concrete type</li>
 * <li>Always set {@code exerciseType} in metadata (for exercises {@link #baseMetadata(Exercise)}
 * derives it from {@code Exercise#getExerciseType()}) or an equivalent type discriminator for
 * lecture units</li>
 * <li>Add corresponding tests in {@code ContentExtractionServiceTest}</li>
 * </ol>
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class ContentExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ContentExtractionService.class);

    private static final String FLAVOR_STRIP_PROMPT_PATH = "/prompts/atlas/flavor_text_strip_prompt.st";

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final String flavorStripModel;

    private final String flavorStripReasoningEffort;

    private final double flavorStripTemperature;

    public ContentExtractionService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, QuizExerciseRepository quizExerciseRepository,
            @Value("${artemis.atlas.flavor-strip-model:gpt-5.4-mini}") String flavorStripModel,
            @Value("${artemis.atlas.flavor-strip-reasoning-effort:medium}") String flavorStripReasoningEffort,
            @Value("${artemis.atlas.flavor-strip-temperature:1.0}") double flavorStripTemperature) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.flavorStripModel = flavorStripModel;
        this.flavorStripReasoningEffort = flavorStripReasoningEffort;
        this.flavorStripTemperature = flavorStripTemperature;
    }

    /**
     * Extracts learning-relevant content from the given learning object, including
     * LLM-based flavor text stripping for supported types.
     *
     * @param learningObject the learning object to extract content from
     * @return a DTO containing the title, learning text, and metadata
     * @throws IllegalArgumentException if the learning object type is not yet supported
     */
    public ExtractedContentDTO extractContent(LearningObject learningObject) {
        return extractContent(learningObject, true);
    }

    /**
     * Extracts learning-relevant content from the given learning object.
     *
     * @param learningObject  the learning object to extract content from
     * @param stripFlavorText whether to apply LLM-based flavor text stripping; pass {@code false}
     *                            in latency-sensitive paths (e.g. synchronous UI requests)
     * @return a DTO containing the title, learning text, and metadata
     * @throws IllegalArgumentException if the learning object type is not yet supported
     */
    public ExtractedContentDTO extractContent(LearningObject learningObject, boolean stripFlavorText) {
        Objects.requireNonNull(learningObject, "learningObject must not be null");
        return switch (learningObject) {
            case ProgrammingExercise programmingExercise -> extractFromProgrammingExercise(programmingExercise, stripFlavorText);
            case TextExercise textExercise -> extractFromTextExercise(textExercise, stripFlavorText);
            case ModelingExercise modelingExercise -> extractFromModelingExercise(modelingExercise, stripFlavorText);
            case FileUploadExercise fileUploadExercise -> extractFromFileUploadExercise(fileUploadExercise, stripFlavorText);
            case QuizExercise quizExercise -> extractFromQuizExercise(quizExercise);
            default -> throw new IllegalArgumentException("Unsupported learning object type: " + learningObject.getClass().getSimpleName());
        };
    }

    /**
     * Remove narrative scaffolding from the given raw text via a small/fast LLM, keeping only
     * pedagogically relevant content. The LLM returns a list of SEARCH/REPLACE edits
     * ({@link FlavorStripEditsDTO}) which are applied locally to the raw text. This minimizes
     * output tokens (only the flavor spans are emitted) and structurally guarantees that
     * kept technical content is byte-identical to the input.
     * <p>
     * If the {@code artemis.atlas.flavor-strip-model} property is empty or no {@link ChatClient} is
     * available, the raw text is returned unchanged (graceful degradation). Null or blank
     * input returns an empty string. Any failure (LLM error, malformed JSON, empty edit list)
     * also falls back to the raw text.
     *
     * @param rawText the raw exercise text
     * @return the cleaned text, or the original text if stripping is disabled or fails
     */
    public String stripFlavorText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        if (flavorStripModel == null || flavorStripModel.isBlank() || chatClient == null) {
            return rawText;
        }
        try {
            // Spring AI's .entity(Class) automatically appends JSON schema / format instructions to
            // the user message; no need to inject them via the prompt template.
            String systemPrompt = templateService.render(FLAVOR_STRIP_PROMPT_PATH, Map.of());
            OpenAiChatOptions.Builder options = buildChatOptions(flavorStripModel, flavorStripReasoningEffort, flavorStripTemperature);
            FlavorStripEditsDTO parsedEdits = chatClient.prompt().system(systemPrompt).user(rawText).options(options).call().entity(FlavorStripEditsDTO.class);
            if (parsedEdits == null || parsedEdits.edits() == null || parsedEdits.edits().isEmpty()) {
                return rawText;
            }
            String edited = applyEdits(rawText, parsedEdits.edits());
            // If no edit span actually matched, the text is unchanged: return it byte-identical rather than
            // running whitespace normalization over content the model never targeted.
            return edited.equals(rawText) ? rawText : normalizeWhitespace(edited);
        }
        catch (Exception e) {
            log.warn("Flavor-text stripping failed; falling back to raw text", e);
            return rawText;
        }
    }

    /**
     * Build the OpenAI chat options for the flavor-strip call. GPT-5 reasoning deployments reject an
     * explicit temperature alongside {@code reasoningEffort}, so exactly one is set: {@code reasoningEffort}
     * when configured, otherwise {@code temperature}. Mirrors {@code CompetencyOrchestrationService.buildChatOptions}.
     */
    static OpenAiChatOptions.Builder buildChatOptions(String model, String reasoningEffort, double temperature) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().deploymentName(model);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            builder.reasoningEffort(reasoningEffort);
        }
        else {
            builder.temperature(temperature);
        }
        return builder;
    }

    /**
     * Apply the given SEARCH/REPLACE edits to {@code rawText} in order. For each edit, the first
     * occurrence of {@code edit.search()} is replaced with {@code edit.replace()} via
     * {@link #findSpan(String, String)} (exact match, then a whitespace-tolerant fallback). Edits
     * whose {@code search} cannot be located are skipped (logged at DEBUG) so a single off-target
     * span does not poison the whole strip.
     * <p>
     * The prompt contract only permits {@code replace} to be empty (pure deletion) or a single
     * grammatical joiner character. To enforce the byte-identical guarantee server-side, edits
     * whose replacement exceeds that minimal value are skipped rather than trusted — a malformed
     * response can therefore never rewrite technical content.
     */
    private String applyEdits(String rawText, List<EditDTO> edits) {
        String working = rawText;
        for (EditDTO edit : edits) {
            if (edit == null || edit.search() == null || edit.search().isEmpty()) {
                continue;
            }
            String replacement = edit.replace() == null ? "" : edit.replace();
            if (replacement.length() > 1) {
                log.debug("Skipping flavor-strip edit; replacement exceeds the allowed minimal value: {}", replacement);
                continue;
            }
            int[] span = findSpan(working, edit.search());
            if (span == null) {
                log.debug("Skipping flavor-strip edit; search span not found in working text: {}", edit.search());
                continue;
            }
            working = working.substring(0, span[0]) + replacement + working.substring(span[1]);
        }
        return working;
    }

    /**
     * Locate the {@code search} span in {@code working}. First tries an exact literal match; if that
     * fails, falls back to a whitespace-tolerant match that ignores leading/trailing whitespace and
     * treats any internal whitespace run as equivalent, while still requiring every non-whitespace
     * character to match exactly. This lets weaker models — whose search spans often differ from the
     * source only in whitespace (extra indentation, single vs. double spaces) — still land their
     * deletions, without ever matching a span whose visible content differs (e.g. a paraphrased word),
     * so kept content stays byte-identical.
     *
     * @return the {@code [start, end)} offsets of the matched span, or {@code null} if not found
     */
    private static int[] findSpan(String working, String search) {
        int exact = working.indexOf(search);
        if (exact >= 0) {
            return new int[] { exact, exact + search.length() };
        }
        String trimmed = search.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        // Build a pattern matching each whitespace-delimited token literally, joined by \s+ for any
        // internal whitespace run. Leading/trailing whitespace is dropped by strip(), so only the
        // non-whitespace skeleton must match; the actual source whitespace inside the span is consumed.
        String[] tokens = trimmed.split("\\s+");
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                regex.append("\\s+");
            }
            regex.append(Pattern.quote(tokens[i]));
        }
        Matcher matcher = Pattern.compile(regex.toString()).matcher(working);
        return matcher.find() ? new int[] { matcher.start(), matcher.end() } : null;
    }

    /**
     * Conservative whitespace cleanup after applying edits: collapse runs of three or more
     * consecutive newlines to two, and strip trailing spaces/tabs from each line. Single
     * spaces, tabs, and existing single-blank-line separators are preserved so that code
     * fences, indentation, and Markdown structure remain intact.
     */
    private String normalizeWhitespace(String text) {
        // Strip trailing horizontal whitespace from every line.
        String stripped = text.replaceAll("(?m)[ \\t]+$", "");
        // Collapse three or more consecutive newlines to exactly two.
        return stripped.replaceAll("\n{3,}", "\n\n");
    }

    private ExtractedContentDTO extractFromProgrammingExercise(ProgrammingExercise exercise, boolean applyFlavorStrip) {
        String title = Objects.requireNonNullElse(exercise.getTitle(), "");
        String raw = Objects.requireNonNullElse(exercise.getProblemStatement(), "");
        String learningText = applyFlavorStrip ? stripFlavorText(raw) : raw;
        return new ExtractedContentDTO(title, learningText, baseMetadata(exercise));
    }

    private ExtractedContentDTO extractFromTextExercise(TextExercise exercise, boolean applyFlavorStrip) {
        String title = Objects.requireNonNullElse(exercise.getTitle(), "");
        String learningText = statementWithSolution(exercise.getProblemStatement(), exercise.getExampleSolution(), applyFlavorStrip);
        return new ExtractedContentDTO(title, learningText, baseMetadata(exercise));
    }

    private ExtractedContentDTO extractFromModelingExercise(ModelingExercise exercise, boolean applyFlavorStrip) {
        String title = Objects.requireNonNullElse(exercise.getTitle(), "");
        // The example-solution *explanation* is prose; the example-solution *model* is serialized Apollon
        // JSON and is deliberately excluded — it is noise for competency reasoning, not learning content.
        String learningText = statementWithSolution(exercise.getProblemStatement(), exercise.getExampleSolutionExplanation(), applyFlavorStrip);
        Map<String, String> metadata = baseMetadata(exercise);
        if (exercise.getDiagramType() != null) {
            metadata.put("diagramType", exercise.getDiagramType().name().toLowerCase(Locale.ROOT));
        }
        return new ExtractedContentDTO(title, learningText, metadata);
    }

    private ExtractedContentDTO extractFromFileUploadExercise(FileUploadExercise exercise, boolean applyFlavorStrip) {
        String title = Objects.requireNonNullElse(exercise.getTitle(), "");
        String learningText = statementWithSolution(exercise.getProblemStatement(), exercise.getExampleSolution(), applyFlavorStrip);
        Map<String, String> metadata = baseMetadata(exercise);
        if (exercise.getFilePattern() != null && !exercise.getFilePattern().isBlank()) {
            metadata.put("filePattern", exercise.getFilePattern().strip());
        }
        return new ExtractedContentDTO(title, learningText, metadata);
    }

    /**
     * Extracts quiz content. Quizzes carry no problem statement, so the learning text is assembled from
     * the questions and their correct answers/solutions ({@link #renderQuizQuestions}).
     * <p>
     * The {@code quizQuestions} collection is {@code LAZY} and extraction runs on the async scheduler
     * thread with no open session, so a persisted quiz is re-fetched with its questions eagerly loaded
     * to avoid a {@code LazyInitializationException}. In-memory quizzes (no id — unit tests) already
     * carry their questions and are used as-is. The unfiltered entity is read on purpose: the LLM needs
     * the correct answers to judge competency fit, and nothing here is exposed to students.
     * <p>
     * Flavor-text stripping is intentionally skipped: quiz content is terse structured Q&amp;A, not the
     * narrative prose the strip pass targets, so it would add latency and an LLM call for no benefit.
     */
    private ExtractedContentDTO extractFromQuizExercise(QuizExercise exercise) {
        QuizExercise source = exercise.getId() != null ? quizExerciseRepository.findByIdWithQuestionsElseThrow(exercise.getId()) : exercise;
        String title = Objects.requireNonNullElse(source.getTitle(), "");
        List<QuizQuestion> questions = source.getQuizQuestions() == null ? List.of() : source.getQuizQuestions();
        Map<String, String> metadata = baseMetadata(source);
        metadata.put("questionCount", Integer.toString(questions.size()));
        return new ExtractedContentDTO(title, renderQuizQuestions(questions), metadata);
    }

    /**
     * Builds the base metadata every exercise carries. A {@link LinkedHashMap} preserves insertion order
     * for deterministic JSON serialization. {@code exerciseType} is derived from the concrete type so it
     * stays correct for every subtype; {@code difficulty} / {@code maxPoints} are added when present.
     */
    private static Map<String, String> baseMetadata(Exercise exercise) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("exerciseType", exercise.getExerciseType().getValue());
        if (exercise.getDifficulty() != null) {
            metadata.put("difficulty", exercise.getDifficulty().name().toLowerCase(Locale.ROOT));
        }
        if (exercise.getMaxPoints() != null) {
            metadata.put("maxPoints", String.format(Locale.ROOT, "%.1f", exercise.getMaxPoints()));
        }
        return metadata;
    }

    /**
     * Flavor-strips the (prose) problem statement like a programming exercise, then appends the labeled
     * example solution verbatim. The solution is not flavor-stripped — it is the reference answer, not
     * narrative scaffolding. Returns just the statement when no solution is present, and just the labeled
     * solution when the statement is blank.
     */
    private String statementWithSolution(@Nullable String problemStatement, @Nullable String exampleSolution, boolean applyFlavorStrip) {
        String raw = Objects.requireNonNullElse(problemStatement, "");
        String statement = applyFlavorStrip ? stripFlavorText(raw) : raw;
        if (exampleSolution == null || exampleSolution.isBlank()) {
            return statement;
        }
        String labeledSolution = "Example solution:\n" + exampleSolution.strip();
        return statement.isBlank() ? labeledSolution : statement + "\n\n" + labeledSolution;
    }

    /** Assembles the pedagogical content of a quiz's questions into a single learning-text block. */
    private static String renderQuizQuestions(List<QuizQuestion> questions) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (QuizQuestion question : questions) {
            if (index > 1) {
                sb.append("\n\n");
            }
            String questionTitle = question.getTitle() == null ? "" : question.getTitle().strip();
            sb.append("Question ").append(index);
            if (!questionTitle.isBlank()) {
                sb.append(": ").append(questionTitle);
            }
            sb.append('\n');
            appendField(sb, null, question.getText());
            appendField(sb, "Hint", question.getHint());
            appendField(sb, "Explanation", question.getExplanation());
            switch (question) {
                case MultipleChoiceQuestion mc -> renderMultipleChoice(sb, mc);
                case ShortAnswerQuestion sa -> renderShortAnswer(sb, sa);
                case DragAndDropQuestion dnd -> renderDragAndDrop(sb, dnd);
                default -> {
                    // Unknown question subtype: the shared title/text/hint/explanation above is still emitted.
                }
            }
            index++;
        }
        return sb.toString().strip();
    }

    private static void renderMultipleChoice(StringBuilder sb, MultipleChoiceQuestion question) {
        List<AnswerOption> options = question.getAnswerOptions();
        if (options == null || options.isEmpty()) {
            return;
        }
        sb.append("Answer options:\n");
        for (AnswerOption option : options) {
            String text = option.getText() == null ? "" : option.getText().strip();
            sb.append("- ").append(text).append(Boolean.TRUE.equals(option.isIsCorrect()) ? " [correct]" : " [incorrect]");
            if (option.getExplanation() != null && !option.getExplanation().isBlank()) {
                sb.append(" (").append(option.getExplanation().strip()).append(')');
            }
            sb.append('\n');
        }
    }

    private static void renderShortAnswer(StringBuilder sb, ShortAnswerQuestion question) {
        Set<ShortAnswerMapping> mappings = question.getCorrectMappings();
        if (mappings != null && !mappings.isEmpty()) {
            List<ShortAnswerMapping> mapped = mappings.stream().filter(mapping -> mapping.getSpot() != null && mapping.getSolution() != null)
                    .sorted(Comparator.comparing(mapping -> Objects.requireNonNullElse(mapping.getSpot().getSpotNr(), 0))).toList();
            if (!mapped.isEmpty()) {
                sb.append("Correct answers by spot:\n");
                for (ShortAnswerMapping mapping : mapped) {
                    sb.append("- Spot ").append(Objects.requireNonNullElse(mapping.getSpot().getSpotNr(), 0)).append(": ")
                            .append(mapping.getSolution().getText() == null ? "" : mapping.getSolution().getText().strip()).append('\n');
                }
                return;
            }
        }
        List<ShortAnswerSolution> solutions = question.getSolutions();
        if (solutions == null || solutions.isEmpty()) {
            return;
        }
        sb.append("Accepted answers:\n");
        for (ShortAnswerSolution solution : solutions) {
            if (solution.getText() != null && !solution.getText().isBlank()) {
                sb.append("- ").append(solution.getText().strip()).append('\n');
            }
        }
    }

    private static void renderDragAndDrop(StringBuilder sb, DragAndDropQuestion question) {
        // Drop locations carry only geometry (no text), so they are referenced by a stable 1-based zone number
        // (their order in the question). The drag items list the available pieces; the correct-mapping block below
        // gives the solution (which piece belongs in which zone) so the LLM can judge the competency fit, not just
        // the presence of labels.
        List<DragItem> dragItems = question.getDragItems();
        if (dragItems == null || dragItems.isEmpty()) {
            return;
        }
        boolean headerWritten = false;
        for (DragItem item : dragItems) {
            if (item.getText() == null || item.getText().isBlank()) {
                continue;
            }
            if (!headerWritten) {
                sb.append("Drag items:\n");
                headerWritten = true;
            }
            sb.append("- ").append(item.getText().strip()).append('\n');
        }
        renderDragAndDropSolution(sb, question);
    }

    /** Appends the correct drag-item-to-drop-zone pairings, referencing each geometry-only drop location by its 1-based position. */
    private static void renderDragAndDropSolution(StringBuilder sb, DragAndDropQuestion question) {
        Set<DragAndDropMapping> mappings = question.getCorrectMappings();
        List<DropLocation> dropLocations = question.getDropLocations();
        if (mappings == null || mappings.isEmpty() || dropLocations == null || dropLocations.isEmpty()) {
            return;
        }
        record ZonePairing(int zone, String itemText) {
        }
        List<ZonePairing> pairings = new ArrayList<>();
        for (DragAndDropMapping mapping : mappings) {
            DragItem item = mapping.getDragItem();
            DropLocation location = mapping.getDropLocation();
            if (item == null || item.getText() == null || item.getText().isBlank() || location == null) {
                continue;
            }
            // indexOf matches by identity for transient locations (null id) and by id once persisted — both resolve
            // to the same instances held in the eagerly-loaded dropLocations list.
            int position = dropLocations.indexOf(location);
            if (position < 0) {
                continue;
            }
            pairings.add(new ZonePairing(position + 1, item.getText().strip()));
        }
        if (pairings.isEmpty()) {
            return;
        }
        pairings.sort(Comparator.comparingInt(ZonePairing::zone).thenComparing(ZonePairing::itemText));
        sb.append("Correct drop mapping:\n");
        for (ZonePairing pairing : pairings) {
            sb.append("- ").append(pairing.itemText()).append(" -> drop zone ").append(pairing.zone()).append('\n');
        }
    }

    /** Appends {@code "<label>: <value>\n"} (or just {@code "<value>\n"} when {@code label} is null), skipping blank values. */
    private static void appendField(StringBuilder sb, @Nullable String label, @Nullable String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (label != null) {
            sb.append(label).append(": ");
        }
        sb.append(value.strip()).append('\n');
    }
}
