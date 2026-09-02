package de.tum.cit.aet.artemis.presentation.dto;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.StrictIntegerDeserializer;
import de.tum.cit.aet.artemis.presentation.domain.PresentationAssessment;

/**
 * DTO for course-level presentation assessments.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentDTO(Long id, @NotBlank @Size(max = 255) String title, @Size(max = 1000) String description,
        @NotNull @Min(1) @Max(10000) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer maxPoints,
        @PositiveOrZero @Max(10000) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer resultPoints, ZonedDateTime presentationDate, Long courseId,
        List<String> studentLogins) {

    /**
     * Creates a DTO from a presentation assessment entity.
     *
     * @param presentationAssessment the entity to map
     * @return the mapped DTO
     */
    public static PresentationAssessmentDTO of(PresentationAssessment presentationAssessment) {
        Long courseId = presentationAssessment.getCourse() != null ? presentationAssessment.getCourse().getId() : null;
        return new PresentationAssessmentDTO(presentationAssessment.getId(), presentationAssessment.getTitle(), presentationAssessment.getDescription(),
                presentationAssessment.getMaxPoints(), presentationAssessment.getResultPoints(), presentationAssessment.getPresentationDate(), courseId, null);
    }
}
