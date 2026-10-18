package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The values of a feature bonus award a tutor creates or changes.
 *
 * @param title         the feature the points are awarded for
 * @param justification why the feature is worth the points, optional
 * @param points        the awarded points, fractions allowed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureBonusAwardRequestDTO(@NotBlank @Size(max = 255) String title, @Size(max = 2000) String justification,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") Double points) {
}
