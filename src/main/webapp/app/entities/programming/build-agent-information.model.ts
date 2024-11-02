import { BaseEntity } from 'app/shared/model/base-entity';
import { BuildJob } from 'app/entities/programming/build-job.model';
import { BuildAgent } from 'app/entities/programming/build-agent.model';

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
    public runningBuildJobs?: BuildJob[];
    public status?: BuildAgentStatus;
    public recentBuildJobs?: BuildJob[];
}
