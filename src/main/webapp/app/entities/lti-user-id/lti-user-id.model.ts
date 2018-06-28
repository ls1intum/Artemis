import { BaseEntity, User } from './../../shared';

export class LtiUserId implements BaseEntity {
    constructor(
        public id?: number,
        public ltiUserId?: string,
        public user?: User,
    ) {
    }
}
