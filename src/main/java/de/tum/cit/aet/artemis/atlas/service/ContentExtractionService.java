package de.tum.cit.aet.artemis.atlas.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.atlas.dto.FlavorStripEdits;
import de.tum.cit.aet.artemis.atlas.dto.FlavorStripEdits.Edit;
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
            @Value("${atlas.flavor-strip-model:gpt-5.4-mini}") String flavorStripModel, @Value("${atlas.flavor-strip-reasoning-effort:low}") String flavorStripReasoningEffort,
            @Value("${atlas.flavor-strip-temperature:1.0}") double flavorStripTemperature) {
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
     * @param learningObject   the learning object to extract content from
     * @param stripFlavorText  whether to apply LLM-based flavor text stripping; pass {@code false}
     *                         in latency-sensitive paths (e.g. synchronous UI requests)
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
     * ({@link FlavorStripEdits}) which are applied locally to the raw text. This minimizes
     * output tokens (only the flavor spans are emitted) and structurally guarantees that
     * kept technical content is byte-identical to the input.
     * <p>
     * If the {@code atlas.flavor-strip-model} property is empty or no {@link ChatClient} is
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
            AzureOpenAiChatOptions.Builder optionsBuilder = AzureOpenAiChatOptions.builder().deploymentName(flavorStripModel).temperature(flavorStripTemperature);
            if (flavorStripReasoningEffort != null && !flavorStripReasoningEffort.isBlank()) {
                optionsBuilder.reasoningEffort(flavorStripReasoningEffort);
            }
            AzureOpenAiChatOptions options = optionsBuilder.build();
            FlavorStripEdits parsedEdits = chatClient.prompt().system(systemPrompt).user(rawText).options(options).call().entity(FlavorStripEdits.class);
            if (parsedEdits == null || parsedEdits.edits() == null || parsedEdits.edits().isEmpty()) {
                return rawText;
            }
            String edited = applyEdits(rawText, parsedEdits.edits());
            return normalizeWhitespace(edited);
        }
        catch (Exception e) {
            log.warn("Flavor-text stripping failed; falling back to raw text", e);
            return rawText;
        }
    }

    /**
     * Apply the given SEARCH/REPLACE edits to {@code rawText} in order. For each edit, the
     * first literal occurrence of {@code edit.search()} is replaced with {@code edit.replace()}.
     * Edits whose {@code search} cannot be found in the working text are skipped (logged at
     * DEBUG) so a single off-target span does not poison the whole strip.
     */
    private String applyEdits(String rawText, List<Edit> edits) {
        String working = rawText;
        for (Edit edit : edits) {
            if (edit == null || edit.search() == null || edit.search().isEmpty()) {
                continue;
            }
            int index = working.indexOf(edit.search());
            if (index < 0) {
                log.debug("Skipping flavor-strip edit; search span not found in working text: {}", edit.search());
                continue;
            }
            String replacement = edit.replace() == null ? "" : edit.replace();
            working = working.substring(0, index) + replacement + working.substring(index + edit.search().length());
        }
        return working;
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
