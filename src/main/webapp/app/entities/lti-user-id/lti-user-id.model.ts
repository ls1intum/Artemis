import { BaseEntity } from 'app/shared';
import { User } from './../../core';

export class LtiUserId implements BaseEntity {
    public id: number;
    public ltiUserId: string;
    public user: User;

    constructor() {}
}
