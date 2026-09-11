package de.tum.cit.aet.artemis.presentation.dto;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;

/**
 * DTO for course-level presentation assessments.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentDTO(Long id, @NotBlank @Size(max = 255) String title, @Size(max = 1000) String description,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000") @Digits(integer = 5, fraction = 2) Double maxPoints, Long courseId, Long exerciseId,
        String exerciseTitle, List<PresentationAssessmentInstanceDTO> instances) {

    /**
     * Creates a DTO from a presentation assessment entity.
     *
     * @param presentationAssessment the entity to map
     * @return the mapped DTO
     */
    public static PresentationAssessmentDTO of(PresentationAssessment presentationAssessment) {
        Long courseId = presentationAssessment.getCourse() != null ? presentationAssessment.getCourse().getId() : null;
        Long exerciseId = presentationAssessment.getExercise() != null ? presentationAssessment.getExercise().getId() : null;
        String exerciseTitle = presentationAssessment.getExercise() != null ? presentationAssessment.getExercise().getTitle() : null;
        return new PresentationAssessmentDTO(presentationAssessment.getId(), presentationAssessment.getTitle(), presentationAssessment.getDescription(),
                presentationAssessment.getMaxPoints(), courseId, exerciseId, exerciseTitle,
                presentationAssessment.getInstances().stream().map(PresentationAssessmentInstanceDTO::of).toList());
    }
}
