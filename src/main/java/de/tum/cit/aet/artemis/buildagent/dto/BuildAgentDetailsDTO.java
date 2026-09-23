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
 * @param buildRunner              the configured build runner display name
 * @param buildRunnerVersion       the version reported by the configured build runner
 */
public record BuildAgentDetailsDTO(long averageBuildDuration, long successfulBuilds, long failedBuilds, long cancelledBuilds, long timedOutBuild, long totalBuilds,
        @Nullable ZonedDateTime lastBuildDate, @NotNull ZonedDateTime startDate, @Nullable String gitRevision, int consecutiveBuildFailures, @Nullable String dockerVersion,
        @NotNull String buildRunner, @Nullable String buildRunnerVersion) implements Serializable {

    /**
     * Deliberately kept at 2 even though runner metadata was added.
     * <p>
     * Hazelcast stores this DTO with plain Java serialization, and a rolling upgrade temporarily runs nodes of both versions. Bumping the serial version would make each
     * version reject the other's entries with an {@code InvalidClassException}, so the build agent overview would break for the duration of every upgrade. Record
     * deserialization tolerates the added components on its own: a stream written by an older node simply leaves them at their default value, which {@link #readResolve()}
     * then fills in.
     */
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Compatibility constructor for callers created before runner metadata was added.
     */
    public BuildAgentDetailsDTO(long averageBuildDuration, long successfulBuilds, long failedBuilds, long cancelledBuilds, long timedOutBuild, long totalBuilds,
            @Nullable ZonedDateTime lastBuildDate, @NotNull ZonedDateTime startDate, @Nullable String gitRevision, int consecutiveBuildFailures, @Nullable String dockerVersion) {
        this(averageBuildDuration, successfulBuilds, failedBuilds, cancelledBuilds, timedOutBuild, totalBuilds, lastBuildDate, startDate, gitRevision, consecutiveBuildFailures,
                dockerVersion, dockerVersion != null ? "Docker" : "Unknown", dockerVersion);
    }

    /**
     * Supplies runner metadata for entries written by a node that did not know about it yet.
     * <p>
     * Record deserialization passes the default value for every component missing from the stream, so {@code buildRunner} arrives as null from an older node. Deriving the
     * runner from the Docker version keeps the build agent overview readable until that node is upgraded and republishes its information.
     *
     * @return this instance, or a copy with the runner metadata derived from the Docker version
     */
    @Serial
    private Object readResolve() {
        if (buildRunner != null) {
            return this;
        }
        return new BuildAgentDetailsDTO(averageBuildDuration, successfulBuilds, failedBuilds, cancelledBuilds, timedOutBuild, totalBuilds, lastBuildDate, startDate, gitRevision,
                consecutiveBuildFailures, dockerVersion);
    }
}
