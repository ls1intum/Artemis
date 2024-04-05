import { StringBaseEntity } from 'app/shared/model/base-entity';
import { RepositoryInfo } from 'app/entities/repository-info.model';
import { JobTimingInfo } from 'app/entities/job-timing-info.model';
import { BuildConfig } from 'app/entities/build-config.model';
import { Result } from 'app/entities/result.model';

export class BuildJob implements StringBaseEntity {
    public id?: string;
    public name?: string;
    public buildAgentAddress?: string;
    public participationId?: number;
    public courseId?: number;
    public exerciseId?: number;
    public retryCount?: number;
    public priority?: number;
    public status?: string;
    public repositoryInfo?: RepositoryInfo;
    public jobTimingInfo?: JobTimingInfo;
    public buildConfig?: BuildConfig;
    public submissionResult?: Result;
}
