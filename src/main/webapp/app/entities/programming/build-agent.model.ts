import { BaseEntity } from 'app/shared/model/base-entity';
import { BuildJob } from 'app/entities/programming/build-job.model';

export class BuildAgent implements BaseEntity {
    public id?: number;
    public name?: string;
    public maxNumberOfConcurrentBuildJobs?: number;
    public numberOfCurrentBuildJobs?: number;
    public runningBuildJobs?: BuildJob[];
    public status?: boolean;
    public recentBuildJobs?: BuildJob[];
}
