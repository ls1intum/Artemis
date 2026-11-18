import { BaseEntity } from 'app/foundation/model/base-entity';
import { User } from 'app/account/user/user.model';

export class AssessmentNote implements BaseEntity {
    public id?: number;

    public creator?: User;
    public createdDate?: Date;
    public lastUpdatedDate?: Date;
    public note?: string;
}
