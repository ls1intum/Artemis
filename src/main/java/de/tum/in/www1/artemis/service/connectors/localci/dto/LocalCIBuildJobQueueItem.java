package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;

import de.tum.in.www1.artemis.domain.enumeration.BuildJobResult;

public record LocalCIBuildJobQueueItem(String id, String name, String buildAgentAddress, long participationId, long courseId, long exerciseId, int retryCount, int priority,
        BuildJobResult status, RepositoryInfo repositoryInfo, JobTimingInfo jobTimingInfo, BuildConfig buildConfig) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
