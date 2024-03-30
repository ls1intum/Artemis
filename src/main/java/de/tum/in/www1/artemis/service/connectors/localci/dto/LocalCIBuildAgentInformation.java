package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record LocalCIBuildAgentInformation(String name, int maxNumberOfConcurrentBuildJobs, int numberOfCurrentBuildJobs, List<LocalCIBuildJobQueueItem> runningBuildJobs,
        boolean status, List<LocalCIBuildJobQueueItem> recentBuildJobs) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor used to update the list of recently finished build jobs
     *
     * @param agentInformation The agent information
     * @param recentBuildJobs  The list of recent build jobs
     */
    public LocalCIBuildAgentInformation(LocalCIBuildAgentInformation agentInformation, List<LocalCIBuildJobQueueItem> recentBuildJobs) {
        this(agentInformation.name(), agentInformation.maxNumberOfConcurrentBuildJobs(), agentInformation.numberOfCurrentBuildJobs(), agentInformation.runningBuildJobs,
                agentInformation.status(), recentBuildJobs);
    }
}
