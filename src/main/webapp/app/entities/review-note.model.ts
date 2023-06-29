import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';

export class ReviewNote implements BaseEntity {
    public id?: number;

    public creator?: User;
    public createdDate?: Date;
    public note?: string;
}
