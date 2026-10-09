package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for course-level presentation assessments.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentDTO(Long id, @NotBlank @Size(max = 255) String title, @Size(max = 1000) String description,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000") @Digits(integer = 5, fraction = 3) Double maxPoints, Long courseId, Long exerciseId,
        String exerciseTitle) {

}
