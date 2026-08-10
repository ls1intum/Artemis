package de.tum.cit.aet.artemis.presentation.dto;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;

/**
 * DTO for course-level presentation assessments.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentDTO(Long id, @NotBlank @Size(max = 255) String title, @Size(max = 1000) String description, @NotNull @Positive Double maxPoints,
        @PositiveOrZero Double resultPoints, ZonedDateTime presentationDate, Long courseId, List<String> studentLogins, Long exerciseId, String exerciseTitle,
        List<PresentationAssessmentInstanceDTO> instances) {

    public PresentationAssessmentDTO(Long id, String title, String description, Double maxPoints, Double resultPoints, ZonedDateTime presentationDate, Long courseId,
            List<String> studentLogins) {
        this(id, title, description, maxPoints, resultPoints, presentationDate, courseId, studentLogins, null, null, List.of());
    }

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
                presentationAssessment.getMaxPoints(), presentationAssessment.getResultPoints(), presentationAssessment.getPresentationDate(), courseId,
                presentationAssessment.getStudents().stream().map(student -> student.getLogin()).sorted().toList(), exerciseId, exerciseTitle,
                presentationAssessment.getInstances().stream().map(PresentationAssessmentInstanceDTO::of).toList());
    }
}
