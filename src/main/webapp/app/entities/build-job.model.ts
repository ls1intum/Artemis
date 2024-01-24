import { StringBaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';

export class BuildJob implements StringBaseEntity {
    public id?: string;
    public name?: string;
    public participationId?: number;
    public repositoryName?: string;
    public repositoryType?: string;
    public commitHash?: string;
    public submissionDate?: dayjs.Dayjs;
    public retryCount?: number;
    public buildStartDate?: dayjs.Dayjs;
    public priority?: number;
    public courseId?: number;
    public isPushToTestRepository?: boolean;
    public buildCompletionDate?: dayjs.Dayjs;
    public status?: string;
}
