package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildAgentInformation(String name, int maxNumberOfConcurrentBuildJobs, int numberOfCurrentBuildJobs, List<BuildJobItem> runningBuildJobs, boolean status,
        List<BuildJobItem> recentBuildJobs) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor used to update the list of recently finished build jobs
     *
     * @param agentInformation The agent information
     * @param recentBuildJobs  The list of recent build jobs
     */
    public BuildAgentInformation(BuildAgentInformation agentInformation, List<BuildJobItem> recentBuildJobs) {
        this(agentInformation.name(), agentInformation.maxNumberOfConcurrentBuildJobs(), agentInformation.numberOfCurrentBuildJobs(), agentInformation.runningBuildJobs,
                agentInformation.status(), recentBuildJobs);
    }
}
