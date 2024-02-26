package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.ZonedDateTime;

public record PyrisBuildLogEntryDTO(ZonedDateTime timestamp, String message) {
}
