package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;

public record PyrisBuildLogEntryDTO(Instant timestamp, String message) {
}
