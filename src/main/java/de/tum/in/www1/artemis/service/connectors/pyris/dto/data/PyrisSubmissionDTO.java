package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.util.List;

public record PyrisSubmissionDTO(int id, String date, String commitHash, boolean isPractice, boolean buildFailed, List<PyrisBuildLogEntryDTO> buildLogEntries,
        PyrisResultDTO latestResult) {
}
