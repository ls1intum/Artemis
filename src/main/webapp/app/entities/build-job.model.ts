import { BaseEntity } from 'app/shared/model/base-entity';

export class BuildJob implements BaseEntity {
    public id?: string;
    public name?: string;
    public participationId?: number;
    public repositoryTypeOrUserName?: string;
    public commitHash?: string;
    public submissionDate?: number;
    public retryCount?: number;
    public buildStartDate?: number;
    public priority?: number;
    public courseId?: number;
}
