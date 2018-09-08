import { IUser } from 'app/core/user/user.model';

export interface ILtiUserId {
    id?: number;
    ltiUserId?: string;
    user?: IUser;
}

export class LtiUserId implements ILtiUserId {
    constructor(public id?: number, public ltiUserId?: string, public user?: IUser) {}
}
