package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.BuildLogEntry;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultQueueItem(BuildResult buildResult, BuildJobItem buildJobItem, List<BuildLogEntry> buildLogs, Throwable exception) implements Serializable {
}
