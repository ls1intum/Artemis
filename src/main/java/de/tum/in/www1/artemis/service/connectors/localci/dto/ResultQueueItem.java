package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultQueueItem(LocalCIBuildResult buildResult, LocalCIBuildJobQueueItem buildJobQueueItem, Throwable exception) implements Serializable {
}
