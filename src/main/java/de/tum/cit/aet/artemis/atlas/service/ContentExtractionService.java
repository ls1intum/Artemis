package de.tum.cit.aet.artemis.atlas.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.atlas.dto.FlavorStripEditsDTO;
import de.tum.cit.aet.artemis.atlas.dto.FlavorStripEditsDTO.EditDTO;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Extracts learning-relevant content from {@link LearningObject}s (exercises and lecture units)
 * into {@link ExtractedContentDTO}s for downstream LLM consumption. Currently supports
 * {@link ProgrammingExercise}; other exercise types and lecture unit types will follow.
 * <p>
 * To add a new learning object type:
 * <ol>
 * <li>Add an {@code instanceof} branch in {@link #extractContent(LearningObject)} for the new
 * {@code LearningObject} subtype</li>
 * <li>Create a private {@code extractFrom*()} method accepting the concrete type</li>
 * <li>Always set {@code exerciseType} in metadata via {@link ExerciseType#getValue()} (for exercises)
 * or an equivalent type discriminator for lecture units</li>
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

    private final String flavorStripModel;

    private final String flavorStripReasoningEffort;

    private final double flavorStripTemperature;

    public ContentExtractionService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService,
            @Value("${artemis.atlas.flavor-strip-model:gpt-5.4-mini}") String flavorStripModel,
            @Value("${artemis.atlas.flavor-strip-reasoning-effort:medium}") String flavorStripReasoningEffort,
            @Value("${artemis.atlas.flavor-strip-temperature:1.0}") double flavorStripTemperature) {
        this.chatClient = chatClient;
        this.templateService = templateService;
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
        if (learningObject instanceof ProgrammingExercise programmingExercise) {
            return extractFromProgrammingExercise(programmingExercise, stripFlavorText);
        }
        throw new IllegalArgumentException("Unsupported learning object type: " + learningObject.getClass().getSimpleName());
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
     * Build the Azure chat options for the flavor-strip call. GPT-5 reasoning deployments reject an
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

        // LinkedHashMap preserves insertion order for deterministic JSON serialization
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("exerciseType", ExerciseType.PROGRAMMING.getValue());

        if (exercise.getDifficulty() != null) {
            metadata.put("difficulty", exercise.getDifficulty().name().toLowerCase(Locale.ROOT));
        }
        if (exercise.getMaxPoints() != null) {
            metadata.put("maxPoints", String.format(Locale.ROOT, "%.1f", exercise.getMaxPoints()));
        }

        return new ExtractedContentDTO(title, learningText, metadata);
    }
}
