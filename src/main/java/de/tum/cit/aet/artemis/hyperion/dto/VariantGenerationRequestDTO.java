package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serializable;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;

/**
 * Wizard request to generate an exercise variant.
 * Intents are expressed by field PRESENCE alone — a null/blank field means "no change on this dimension".
 * No changeX booleans (they can only contradict the field they gate) and NO client-supplied title — the
 * planner generates one to fit the transformed exercise.
 *
 * @param targetDifficulty       null = keep difficulty
 * @param domainText             null/blank = keep domain (free-text re-theme, e.g. "space station inventory")
 * @param narrativeStyle         null = stay consistent with the source's narrative (see {@link VariantNarrativeStyle})
 * @param additionalInstructions null/blank = none (free-text transformation requests)
 * @param placement              where to put the variant; SAME_EXAM_GROUP is implicit for exam exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VariantGenerationRequestDTO(@Nullable DifficultyLevel targetDifficulty, @Nullable @Size(max = MAX_FREE_TEXT_LENGTH) String domainText,
        @Nullable VariantNarrativeStyle narrativeStyle, @Nullable @Size(max = MAX_FREE_TEXT_LENGTH) String additionalInstructions, @Valid VariantPlacementDTO placement)
        implements Serializable {

    // The two free-text fields flow verbatim into the planner prompt; cap their length to bound the prompt token
    // cost. Generous enough for a paragraph of domain description or custom instructions.
    private static final int MAX_FREE_TEXT_LENGTH = 2000;

    /**
     * @return true when at least one of the intent fields is present — the resource rejects requests
     *         without any intent with 400
     */
    public boolean hasAnyIntent() {
        return targetDifficulty != null || narrativeStyle != null || (domainText != null && !domainText.isBlank())
                || (additionalInstructions != null && !additionalInstructions.isBlank());
    }
}
