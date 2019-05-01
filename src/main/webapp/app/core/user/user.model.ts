import { Account } from '../../core';
import { Moment } from 'moment';

export class User extends Account {
    constructor(
        public id: number | null,
        login: string | null,
        firstName: string | null,
        lastName: string | null,
        email: string | null,
        activated: boolean | null,
        langKey: string | null,
        authorities: string[] | null,
        public groups: string[] | null,
        public createdBy: string | null,
        public createdDate: Date | null,
        public lastModifiedBy: string | null,
        public lastModifiedDate: Date | null,
        public lastNotificationRead: Moment | null,
        public password: string | null,
        imageUrl: string | null,
    ) {
        super(activated, authorities, email, firstName, langKey, lastName, login, imageUrl);
    }
}
