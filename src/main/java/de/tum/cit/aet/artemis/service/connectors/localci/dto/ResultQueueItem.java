package de.tum.cit.aet.artemis.service.connectors.localci.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.BuildLogEntry;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultQueueItem(BuildResult buildResult, BuildJobQueueItem buildJobQueueItem, List<BuildLogEntry> buildLogs, Throwable exception) implements Serializable {
}
