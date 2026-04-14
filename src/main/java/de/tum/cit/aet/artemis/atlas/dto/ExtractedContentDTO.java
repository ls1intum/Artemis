package de.tum.cit.aet.artemis.atlas.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Extracted learning-relevant content from an exercise or lecture unit, for LLM consumption.
 * <p>
 * Metadata contract:
 * <ul>
 * <li>{@code exerciseType} (required) — value from {@code ExerciseType.getValue()}</li>
 * <li>{@code difficulty} (optional) — lowercase {@code DifficultyLevel} name</li>
 * <li>{@code maxPoints} (optional) — string representation of the max points</li>
 * </ul>
 * New exercise types may add type-specific keys (e.g. {@code questionCount} for quizzes).
 *
 * @param title                 the human-readable title of the learning object
 * @param extractedLearningText the raw learning-relevant text (e.g. problem statement markdown)
 * @param metadata              type-specific attributes; keys and value formats documented above
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractedContentDTO(String title, String extractedLearningText, Map<String, String> metadata) {

    public ExtractedContentDTO {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
