package de.tum.cit.aet.artemis.core.dto.export;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for exporting competency progress data for GDPR user data export.
 * Includes course information so the user knows which course each competency belongs to.
 *
 * @param courseId         the ID of the course
 * @param courseTitle      the title of the course
 * @param competencyId     the ID of the competency
 * @param competencyTitle  the title of the competency
 * @param progress         the progress value (0.0 to 1.0)
 * @param confidence       the confidence value (0.0 to 1.0)
 * @param lastModifiedDate the timestamp when the progress was last modified
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserCompetencyProgressExportDTO(Long courseId, String courseTitle, Long competencyId, String competencyTitle, Double progress, Double confidence,
        Instant lastModifiedDate) {
}
