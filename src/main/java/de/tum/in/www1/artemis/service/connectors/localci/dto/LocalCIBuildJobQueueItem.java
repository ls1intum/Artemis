package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;

public record LocalCIBuildJobQueueItem(long id, String name, String buildAgentAddress, long participationId, String repositoryName, RepositoryType repositoryType,
        String commitHash, ZonedDateTime submissionDate, int retryCount, ZonedDateTime buildStartDate, ZonedDateTime buildCompletionDate, int priority, long courseId,
        RepositoryType triggeredByPushTo, String dockerImage) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
