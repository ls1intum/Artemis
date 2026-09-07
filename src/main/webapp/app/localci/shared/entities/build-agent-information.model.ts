import { BaseEntity } from 'app/foundation/model/base-entity';
import { BuildJob } from 'app/localci/shared/entities/build-job.model';
import { BuildAgent } from 'app/localci/shared/entities/build-agent.model';
import dayjs from 'dayjs/esm';

export enum BuildAgentStatus {
    ACTIVE = 'ACTIVE',
    PAUSED = 'PAUSED',
    SELF_PAUSED = 'SELF_PAUSED',
    IDLE = 'IDLE',
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

/**
 * The network addresses a build agent is observed to connect to the cluster from.
 *
 * Recorded by the core nodes rather than reported by the agent, which is what makes them usable for deciding whether a
 * clone really comes from that agent. Several agents behind one NAT gateway legitimately share an address, and an agent
 * that is reconnecting can briefly show more than one.
 */
export class BuildAgentAddressInfo {
    public agentName?: string;
    public addresses?: string[];
    /** ISO timestamp as sent by the server; not parsed into dayjs because nothing renders it yet. */
    public observedAt?: string;
    /** False when an observed address lies outside the configured build agent networks; such an agent cannot clone. */
    public withinAllowlist?: boolean;
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
    public buildRunner?: string;
    public buildRunnerVersion?: string;
}
