package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisSubmissionDTO(long id, Instant date, Map<String, String> repository, boolean isPractice, boolean buildFailed, List<PyrisBuildLogEntryDTO> buildLogEntries,
        PyrisResultDTO latestResult) {
}
