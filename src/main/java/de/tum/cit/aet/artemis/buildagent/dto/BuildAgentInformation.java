package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildAgentInformation(BuildAgentDTO buildAgent, int maxNumberOfConcurrentBuildJobs, int numberOfCurrentBuildJobs, List<BuildJobQueueItem> runningBuildJobs,
        BuildAgentStatus status, String publicSshKey, BuildAgentDetailsDTO buildAgentDetails) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor used to update the list of recently finished build jobs
     *
     * @param agentInformation The agent information
     */
    public BuildAgentInformation(BuildAgentInformation agentInformation) {
        this(agentInformation.buildAgent(), agentInformation.maxNumberOfConcurrentBuildJobs(), agentInformation.numberOfCurrentBuildJobs(), agentInformation.runningBuildJobs,
                agentInformation.status(), agentInformation.publicSshKey(), agentInformation.buildAgentDetails());
    }

    public enum BuildAgentStatus {
        ACTIVE, IDLE, PAUSED, SELF_PAUSED
    }
}
