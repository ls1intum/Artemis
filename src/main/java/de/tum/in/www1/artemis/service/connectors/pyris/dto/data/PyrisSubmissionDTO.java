package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PyrisSubmissionDTO(long id, Instant date, Map<String, String> repository, boolean isPractice, boolean buildFailed, List<PyrisBuildLogEntryDTO> buildLogEntries,
        PyrisResultDTO latestResult) {
}
