package de.tum.cit.aet.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildAgentInformation(String name, int maxNumberOfConcurrentBuildJobs, int numberOfCurrentBuildJobs, List<BuildJobQueueItem> runningBuildJobs, boolean status,
        List<BuildJobQueueItem> recentBuildJobs, String publicSshKey) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor used to update the list of recently finished build jobs
     *
     * @param agentInformation The agent information
     * @param recentBuildJobs  The list of recent build jobs
     */
    public BuildAgentInformation(BuildAgentInformation agentInformation, List<BuildJobQueueItem> recentBuildJobs) {
        this(agentInformation.name(), agentInformation.maxNumberOfConcurrentBuildJobs(), agentInformation.numberOfCurrentBuildJobs(), agentInformation.runningBuildJobs,
                agentInformation.status(), recentBuildJobs, agentInformation.publicSshKey());
    }
}
