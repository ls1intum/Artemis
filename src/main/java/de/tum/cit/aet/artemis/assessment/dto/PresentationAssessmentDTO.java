package de.tum.cit.aet.artemis.assessment.dto;

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
 * DTO for course-level presentation assessments.
 * <p>
 * {@code rubricCriteria} is only sent by the client: a presentation graded by rubric needs it on creation, and on an update a provided list replaces the
 * rubric while omission leaves it unchanged. Responses never contain it; the rubric is loaded through its own endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentDTO(Long id, @NotBlank @Size(max = 255) String title, @Size(max = 1000) String description, @NotNull PresentationGradingMethod gradingMethod,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000") @Digits(integer = 5, fraction = 3) Double maxPoints, Long courseId, Long exerciseId, String exerciseTitle,
        @Nullable @Size(min = 1, max = 20) List<@NotNull @Valid PresentationRubricCriterionDTO> rubricCriteria) {

    public PresentationAssessmentDTO(Long id, String title, String description, PresentationGradingMethod gradingMethod, Double maxPoints, Long courseId, Long exerciseId,
            String exerciseTitle) {
        this(id, title, description, gradingMethod, maxPoints, courseId, exerciseId, exerciseTitle, null);
    }
}
