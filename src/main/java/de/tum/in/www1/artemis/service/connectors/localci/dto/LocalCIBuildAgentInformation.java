package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record LocalCIBuildAgentInformation(String name, int maxNumberOfConcurrentBuildJobs, int numberOfCurrentBuildJobs, List<LocalCIBuildJobQueueItem> runningBuildJobs)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
