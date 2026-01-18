package de.tum.cit.aet.artemis.core.dto.export;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for exporting Iris chat session data for course archival.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionExportDTO(Long sessionId, Long userId, ZonedDateTime creationDate, List<IrisMessageExportDTO> messages) {
}
