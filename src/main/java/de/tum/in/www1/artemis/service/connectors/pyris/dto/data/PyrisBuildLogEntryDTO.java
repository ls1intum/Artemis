package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.LocalDateTime;

public record PyrisBuildLogEntryDTO(LocalDateTime timestamp, String message) {
}
