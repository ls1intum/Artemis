import { BaseEntity } from 'app/shared/model/base-entity';
import { BuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { BuildAgent } from 'app/buildagent/shared/entities/build-agent.model';
import dayjs from 'dayjs/esm';

export enum BuildAgentStatus {
    ACTIVE = 'ACTIVE',
    PAUSED = 'PAUSED',
    SELF_PAUSED = 'SELF_PAUSED',
    IDLE = 'IDLE',
    /**
     * The agent is paused because a maintenance action (cache cleanup, cache wipe, or Docker image clearing) is
     * currently running. Distinct from {@link PAUSED} so operators can tell scheduled or admin-triggered
     * maintenance windows apart from administrative pauses.
     */
    MAINTENANCE = 'MAINTENANCE',
}

export class BuildAgentInformation implements BaseEntity {
    public id?: number;
    public buildAgent?: BuildAgent;
    public maxNumberOfConcurrentBuildJobs?: number;
    public numberOfCurrentBuildJobs?: number;
    public status?: BuildAgentStatus;
    public runningBuildJobs?: BuildJob[];
    public recentBuildJobs?: BuildJob[];
    public buildAgentDetails?: BuildAgentDetails;
    public pauseAfterConsecutiveBuildFailures?: number;
}

export class BuildAgentDetails {
    public averageBuildDuration?: number;
    public successfulBuilds?: number;
    public failedBuilds?: number;
    public cancelledBuilds?: number;
    public timedOutBuild?: number;
    public totalBuilds?: number;
    public lastBuildDate?: dayjs.Dayjs;
    public startDate?: dayjs.Dayjs;
    public gitRevision?: string;
    public consecutiveBuildFailures?: number;
    public dockerVersion?: string;
    /**
     * Total bytes on the filesystem hosting the agent's cache directory (or `/` if no cache is configured).
     * Refreshed on every periodic BuildAgentInformation push (cheap `getFileStore` syscall).
     */
    public diskTotalBytes?: number;
    /** Usable (free-for-non-root) bytes on the same filesystem; same refresh cadence as {@link diskTotalBytes}. */
    public diskUsableBytes?: number;
    /**
     * On-disk size of the configured Maven dependency cache. Refreshed on a slower 5-minute cadence by the build
     * agent so the periodic push is never blocked on a multi-GB filesystem walk. `0` when no Maven cache is
     * configured.
     */
    public mavenCacheBytes?: number;
    /** Same as {@link mavenCacheBytes} for the Gradle cache. */
    public gradleCacheBytes?: number;
    /**
     * Sum of reported sizes of Docker images that are not currently bound to a running container — i.e. images
     * that "Reclaim disk" → "Unused Docker images" would delete. `0` when Docker is unavailable.
     */
    public dockerUnusedImageBytes?: number;
    /** Count of those unused Docker images. */
    public dockerUnusedImageCount?: number;
}
