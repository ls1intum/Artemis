package de.tum.cit.aet.artemis.assessment.dto;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_RUBRIC_CRITERIA;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationGradingMethod;

/**
 * Request data for creating or updating a course-level presentation assessment.
 * <p>
 * A presentation graded by rubric needs {@code rubricCriteria} on creation; on an update a provided list replaces the rubric while omission leaves it unchanged.
 * An update with a {@code version} is rejected once the presentation assessment changed after the client loaded that version.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentRequestDTO(Long id, @NotBlank @Size(max = 255) String title, @Size(max = 1000) String description,
        @NotNull PresentationGradingMethod gradingMethod, @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000") @Digits(integer = 5, fraction = 3) Double maxPoints,
        Long courseId, Long exerciseId, @Nullable @Size(min = 1, max = MAX_RUBRIC_CRITERIA) List<@NotNull @Valid PresentationRubricCriterionDTO> rubricCriteria,
        @Nullable Long version) {
}
