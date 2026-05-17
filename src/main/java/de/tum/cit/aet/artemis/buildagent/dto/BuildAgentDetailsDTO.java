package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

/**
 * Data transfer object containing detailed information about a build agent's status and performance metrics.
 * This information is stored in the distributed Hazelcast map and displayed in the build agent details UI.
 * <p>
 * The disk-statistics fields ({@code diskTotalBytes}, {@code diskUsableBytes}, {@code mavenCacheBytes},
 * {@code gradleCacheBytes}, {@code dockerUnusedImageBytes}, {@code dockerUnusedImageCount}) are populated by
 * {@code BuildAgentInformationService}. The two filesystem-level numbers come from a cheap
 * {@code Files.getFileStore} call on every 10-second push; the four heavier numbers (cache walks + Docker daemon
 * enumeration) refresh on a slower 5-minute cadence so a multi-GB cache cannot stall the periodic push. All disk
 * fields are {@code 0} until the slow-stats scheduler has run once, and when Docker is unavailable / no cache is
 * configured.
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
 * @param diskTotalBytes           total bytes on the filesystem hosting the cache (or {@code /} if no cache is
 *                                     configured). {@code 0} until first measurement.
 * @param diskUsableBytes          usable (free-for-non-root) bytes on the same filesystem. {@code 0} until first
 *                                     measurement.
 * @param mavenCacheBytes          on-disk size of the configured Maven cache; {@code 0} when no Maven cache is
 *                                     configured.
 * @param gradleCacheBytes         on-disk size of the configured Gradle cache; {@code 0} when no Gradle cache is
 *                                     configured.
 * @param dockerUnusedImageBytes   total reported size of Docker images that are not currently bound to a running
 *                                     container; {@code 0} when Docker is unavailable.
 * @param dockerUnusedImageCount   count of those unused Docker images
 */
public record BuildAgentDetailsDTO(long averageBuildDuration, long successfulBuilds, long failedBuilds, long cancelledBuilds, long timedOutBuild, long totalBuilds,
        @Nullable ZonedDateTime lastBuildDate, @NotNull ZonedDateTime startDate, @Nullable String gitRevision, int consecutiveBuildFailures, @Nullable String dockerVersion,
        long diskTotalBytes, long diskUsableBytes, long mavenCacheBytes, long gradleCacheBytes, long dockerUnusedImageBytes, int dockerUnusedImageCount) implements Serializable {

    // Keep the serialVersionUID stable when adding compatible fields (Java's default Serializable allows readers
    // on older versions to consume newer payloads, filling absent fields with their type defaults — `0L` for long
    // here, which doubles as the documented "not yet populated" sentinel). Bumping the UID would force a rolling
    // upgrade to coordinate a cluster-wide restart, which Artemis does not currently require.
    @Serial
    private static final long serialVersionUID = 2L;
}
