package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationGradingMethod;

/**
 * DTO for course-level presentation assessments.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentDTO(Long id, String title, String description, PresentationGradingMethod gradingMethod, Double maxPoints, Long courseId, Long exerciseId,
        String exerciseTitle) {
}
