import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';

export class AssessmentNote implements BaseEntity {
    public id?: number;

    public creator?: User;
    public createdDate?: Date;
    public lastUpdatedDate?: Date;
    public note?: string;
}
