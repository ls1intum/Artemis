import { BaseEntity, User } from './../../shared';

export class LtiUserId implements BaseEntity {

    public id: number;
    public ltiUserId: string;
    public user: User;

    constructor() {
    }
}
