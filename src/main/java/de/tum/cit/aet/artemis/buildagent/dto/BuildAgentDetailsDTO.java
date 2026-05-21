package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

/**
 * Data transfer object containing detailed information about a build agent's status and performance metrics.
 * This information is stored in the distributed Hazelcast map and displayed in the build agent details UI.
 *
 * @param averageBuildDuration     the average duration of builds in seconds
 * @param successfulBuilds         the total number of successful builds processed by this agent
 * @param failedBuilds             the total number of failed builds processed by this agent
 * @param cancelledBuilds          the total number of cancelled builds processed by this agent
 * @param timedOutBuild            the total number of timed out builds processed by this agent
 * @param totalBuilds              the total number of builds processed by this agent
 * @param lastBuildDate            the date and time of the last build, or null if no builds have been processed
 * @param startDate                the date and time when this build agent was started
 * @param gitRevision              the Git commit hash of the build agent's code version, or null if unavailable
 * @param consecutiveBuildFailures the number of consecutive build failures (used for auto-pause functionality)
 * @param dockerVersion            the version of Docker running on this build agent, or null if unavailable
 */
public record BuildAgentDetailsDTO(long averageBuildDuration, long successfulBuilds, long failedBuilds, long cancelledBuilds, long timedOutBuild, long totalBuilds,
        @Nullable ZonedDateTime lastBuildDate, @NotNull ZonedDateTime startDate, @Nullable String gitRevision, int consecutiveBuildFailures, @Nullable String dockerVersion)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;
}
