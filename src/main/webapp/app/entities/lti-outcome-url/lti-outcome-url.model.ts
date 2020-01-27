import { BaseEntity } from 'app/shared';
import { User } from 'app/core/user/user.model';
import { Exercise } from '../exercise';

export class LtiOutcomeUrl implements BaseEntity {
    public id: number;
    public url: string;
    public sourcedId: string;
    public user: User;
    public exercise: Exercise;

    constructor() {}
}
