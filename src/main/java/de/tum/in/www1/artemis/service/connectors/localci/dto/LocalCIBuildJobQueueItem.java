package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

public record LocalCIBuildJobQueueItem(long id, String name, String buildAgentAddress, long participationId, String repositoryTypeOrUserName, String commitHash,
        ZonedDateTime submissionDate, int retryCount, ZonedDateTime buildStartDate, int priority, long courseId, boolean isPushToTestOrAuxRepository) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
