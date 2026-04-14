package de.tum.cit.aet.artemis.atlas.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Extracted learning-relevant content from an exercise or lecture unit, for LLM consumption.
 * <p>
 * Metadata contract:
 * <ul>
 * <li>{@code exerciseType} (required) — value from {@link de.tum.cit.aet.artemis.exercise.domain.ExerciseType#getValue()}</li>
 * <li>{@code difficulty} (optional) — lowercase {@link de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel} name</li>
 * <li>{@code maxPoints} (optional) — string representation of the max points</li>
 * </ul>
 * New exercise types may add type-specific keys (e.g. {@code questionCount} for quizzes).
 *
 * @param title                 the human-readable title of the learning object
 * @param extractedLearningText the raw learning-relevant text (e.g. problem statement markdown)
 * @param metadata              type-specific attributes; keys and value formats documented above
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExtractedContentDTO(String title, String extractedLearningText, Map<String, String> metadata) {

    /**
     * Serializes this DTO to a compact JSON string suitable for LLM prompt injection.
     *
     * @return JSON representation of this record
     * @throws JsonProcessingException if serialization fails
     */
    public String toJson() throws JsonProcessingException {
        return JsonObjectMapper.get().writeValueAsString(this);
    }
}
