package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;

/**
 * Wizard request to generate an exercise variant (plan Section 5.1).
 * Intents are expressed by field PRESENCE alone — a null/blank field means "no change on this dimension".
 * No changeX booleans (they can only contradict the field they gate) and NO client-supplied title — the
 * planner generates one to fit the transformed exercise (Section 2.4).
 *
 * @param targetDifficulty       null = keep difficulty
 * @param domainText             null/blank = keep domain (free-text re-theme, e.g. "space station inventory")
 * @param additionalInstructions null/blank = none (free-text transformation requests)
 * @param placement              where to put the variant; SAME_EXAM_GROUP is implicit for exam exercises (Section 5.5)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantGenerationRequestDTO(@Nullable DifficultyLevel targetDifficulty, @Nullable String domainText, @Nullable String additionalInstructions,
        VariantPlacementDTO placement) implements Serializable {

    /**
     * @return true when at least one of the three intent fields is present — the resource rejects requests
     *         without any intent with 400 (plan Section 5.1, "Validation")
     */
    public boolean hasAnyIntent() {
        return targetDifficulty != null || (domainText != null && !domainText.isBlank()) || (additionalInstructions != null && !additionalInstructions.isBlank());
    }

    // TODO (Sonnet): Reasonable @Size limits on the free-text fields (they go into prompts — bound the token cost).
}
