import { BaseEntity } from 'app/shared/model/base-entity';
import { BuildJob } from 'app/entities/programming/build-job.model';
import { BuildAgent } from 'app/entities/programming/build-agent.model';
import dayjs from 'dayjs/esm';

export enum BuildAgentStatus {
    ACTIVE = 'ACTIVE',
    PAUSED = 'PAUSED',
    IDLE = 'IDLE',
}

export class BuildAgentInformation implements BaseEntity {
    public id?: number;
    public buildAgent?: BuildAgent;
    public maxNumberOfConcurrentBuildJobs?: number;
    public numberOfCurrentBuildJobs?: number;
    public status?: BuildAgentStatus;
    public recentBuildJobs?: BuildJob[];
    public buildAgentDetails?: BuildAgentDetails;
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
}
