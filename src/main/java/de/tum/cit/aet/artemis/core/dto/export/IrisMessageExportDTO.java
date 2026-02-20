package de.tum.cit.aet.artemis.core.dto.export;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for exporting Iris message data for course archival.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageExportDTO(Long id, ZonedDateTime sentAt, String sender, String content, Boolean helpful) {
}
