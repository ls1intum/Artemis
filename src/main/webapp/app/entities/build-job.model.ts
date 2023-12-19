import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';

export class BuildJob implements BaseEntity {
    public id?: number;
    public name?: string;
    public participationId?: number;
    public repositoryTypeOrUserName?: string;
    public commitHash?: string;
    public submissionDate?: dayjs.Dayjs;
    public retryCount?: number;
    public buildStartDate?: dayjs.Dayjs;
    public priority?: number;
    public courseId?: number;
    public isPushToTestRepository?: boolean;
}
