import { IUser } from 'app/core/user/user.model';
import { IExercise } from 'app/shared/model//exercise.model';

export interface ILtiOutcomeUrl {
    id?: number;
    url?: string;
    sourcedId?: string;
    user?: IUser;
    exercise?: IExercise;
}

export class LtiOutcomeUrl implements ILtiOutcomeUrl {
    constructor(public id?: number, public url?: string, public sourcedId?: string, public user?: IUser, public exercise?: IExercise) {}
}
