package de.tum.cit.aet.artemis.presentation.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;

/**
 * DTO for course-level presentation assessments.
 */
public record PresentationAssessmentDTO(Long id, @NotBlank String title, String description, @NotNull @Positive Double maxPoints, ZonedDateTime presentationDate, Long courseId) {

    /**
     * Creates a DTO from a presentation assessment entity.
     *
     * @param presentationAssessment the entity to map
     * @return the mapped DTO
     */
    public static PresentationAssessmentDTO of(PresentationAssessment presentationAssessment) {
        Long courseId = presentationAssessment.getCourse() != null ? presentationAssessment.getCourse().getId() : null;
        return new PresentationAssessmentDTO(presentationAssessment.getId(), presentationAssessment.getTitle(), presentationAssessment.getDescription(),
                presentationAssessment.getMaxPoints(), presentationAssessment.getPresentationDate(), courseId);
    }
}
