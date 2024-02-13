package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serializable;

public record ResultQueueItem(LocalCIBuildResult buildResult, LocalCIBuildJobQueueItem buildJobQueueItem, Throwable exception) implements Serializable {
}
