import { StringBaseEntity } from 'app/foundation/model/base-entity';
import { RepositoryInfo, TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { JobTimingInfo } from 'app/localci/shared/entities/job-timing-info.model';
import { BuildConfig } from 'app/localci/shared/entities/build-config.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { BuildAgent } from 'app/localci/shared/entities/build-agent.model';

export class BuildJob implements StringBaseEntity {
    public id?: string;
    public name?: string;
    public buildAgent?: BuildAgent;
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

export class FinishedBuildJob implements StringBaseEntity {
    public id?: string;
    public name?: string;
    public buildAgentAddress?: string;
    public participationId?: number;
    public courseId?: number;
    public exerciseId?: number;
    public retryCount?: number;
    public priority?: number;
    public status?: string;
    public triggeredByPushTo?: TriggeredByPushTo;
    public repositoryName?: string;
    public repositoryType?: string;
    public buildSubmissionDate?: dayjs.Dayjs;
    public buildStartDate?: dayjs.Dayjs;
    public buildCompletionDate?: dayjs.Dayjs;
    public buildDuration?: string;
    public commitHash?: string;
    public submissionResult?: Result;
}

/**
 * Combined shape of a build job as consumed by the build job detail view, which handles both a
 * {@link BuildJob} (queued/running) and a {@link FinishedBuildJob}. The two entities have overlapping
 * but non-identical structures, so every field is optional and property presence is checked at runtime.
 */
export type BuildJobDetail = {
    id?: string;
    name?: string;
    buildAgent?: BuildAgent;
    buildAgentAddress?: string;
    participationId?: number;
    courseId?: number;
    exerciseId?: number;
    retryCount?: number;
    priority?: number;
    status?: string;
    repositoryInfo?: RepositoryInfo;
    repositoryName?: string;
    repositoryType?: string;
    triggeredByPushTo?: TriggeredByPushTo;
    jobTimingInfo?: JobTimingInfo;
    buildConfig?: BuildConfig;
    buildSubmissionDate?: dayjs.Dayjs;
    buildStartDate?: dayjs.Dayjs;
    buildCompletionDate?: dayjs.Dayjs;
    buildDuration?: string | number;
    commitHash?: string;
    submissionResult?: Result;
};

export class BuildJobStatistics {
    public totalBuilds: number = 0;
    public successfulBuilds: number = 0;
    public failedBuilds: number = 0;
    public cancelledBuilds: number = 0;
    public timeOutBuilds: number = 0;
    public missingBuilds: number = 0;
}

export enum SpanType {
    DAY = 1,
    WEEK = 7,
    MONTH = 30,
}
