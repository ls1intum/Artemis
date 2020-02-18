import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';

export class LtiUserId implements BaseEntity {
    public id: number;
    public ltiUserId: string;
    public user: User;

    constructor() {}
}
