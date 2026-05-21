package de.tum.cit.aet.artemis.core.dto.export;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for exporting competency progress data for course archival.
 *
 * @param competencyId     the ID of the competency
 * @param competencyTitle  the title of the competency
 * @param userLogin        the login of the user
 * @param progress         the progress value (0.0 to 1.0)
 * @param confidence       the confidence value (0.0 to 1.0)
 * @param lastModifiedDate the timestamp when the progress was last modified
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyProgressExportDTO(Long competencyId, String competencyTitle, String userLogin, Double progress, Double confidence, Instant lastModifiedDate) {
}
