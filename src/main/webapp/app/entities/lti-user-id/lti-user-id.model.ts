import { BaseEntity } from 'app/shared';
import { User } from 'app/core/user/user.model';

export class LtiUserId implements BaseEntity {
    public id: number;
    public ltiUserId: string;
    public user: User;

    constructor() {}
}
