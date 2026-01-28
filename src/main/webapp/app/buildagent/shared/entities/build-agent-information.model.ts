import { BaseEntity } from 'app/shared/model/base-entity';
import { BuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { BuildAgent } from 'app/buildagent/shared/entities/build-agent.model';
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
}
