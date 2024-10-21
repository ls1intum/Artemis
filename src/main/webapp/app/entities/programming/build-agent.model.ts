import { BaseEntity } from 'app/shared/model/base-entity';
import { BuildJob } from 'app/entities/programming/build-job.model';

export enum BuildAgentStatus {
    ACTIVE = 'ACTIVE',
    PAUSED = 'PAUSED',
    IDLE = 'IDLE',
}

export class BuildAgent {
    public name?: string;
    public memberAddress?: string;
    public displayName?: string;
}

export class BuildAgentInformation implements BaseEntity {
    public id?: number;
    public buildAgent?: BuildAgent;
    public maxNumberOfConcurrentBuildJobs?: number;
    public numberOfCurrentBuildJobs?: number;
    public runningBuildJobs?: BuildJob[];
    public status?: BuildAgentStatus;
    public recentBuildJobs?: BuildJob[];
}
