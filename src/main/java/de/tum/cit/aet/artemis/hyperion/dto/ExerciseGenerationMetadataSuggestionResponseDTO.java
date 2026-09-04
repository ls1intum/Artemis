package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything Artemis can work out about an exercise from the brief alone, so the instructor states their intent once instead of filling in a form.
 * <p>
 * Only the title and the difficulty are suggestions the instructor is meant to weigh; the short name, the package name and the points are derived from the title and shown as what
 * will be created. All of them are valid as returned: the title and the short name are free, and the package name satisfies the rule the exercise will be validated against.
 *
 * @param title       a title Artemis accepts as-is and no programming exercise in the course uses yet
 * @param shortName   a short name matching {@link de.tum.cit.aet.artemis.core.config.Constants#SHORT_NAME_PATTERN}, not taken in the course, and not already claimed by another
 *                        exercise's project key anywhere in this Artemis instance
 * @param packageName the Java package derived from the short name
 * @param difficulty  the difficulty the brief implies, defaulting to {@link DifficultyLevel#MEDIUM} when it implies none
 * @param maxPoints   the points the draft is created with; a constant rather than a guess, and an ordinary exercise edit afterwards
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "The metadata Artemis derives for an exercise it is about to generate")
public record ExerciseGenerationMetadataSuggestionResponseDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String shortName, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String packageName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DifficultyLevel difficulty, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double maxPoints) {
}
