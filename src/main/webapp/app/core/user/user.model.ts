import { Account } from 'app/core/user/account.model';
import { Moment } from 'moment';

export class User extends Account {
    public id?: number;
    public groups?: string[];
    public createdBy?: string;
    public createdDate?: Date;
    public lastModifiedBy?: string;
    public lastModifiedDate?: Date;
    public lastNotificationRead?: Moment;
    public visibleRegistrationNumber?: string;
    public password?: string;

    constructor(
        id?: number,
        login?: string,
        firstName?: string,
        lastName?: string,
        email?: string,
        activated?: boolean,
        langKey?: string,
        authorities?: string[],
        groups?: string[],
        createdBy?: string,
        createdDate?: Date,
        lastModifiedBy?: string,
        lastModifiedDate?: Date,
        lastNotificationRead?: Moment,
        password?: string,
        imageUrl?: string,
    ) {
        super(activated, authorities, email, firstName, langKey, lastName, login, imageUrl);
        this.id = id;
        this.groups = groups;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
        this.lastModifiedBy = lastModifiedBy;
        this.lastModifiedDate = lastModifiedDate;
        this.lastNotificationRead = lastNotificationRead;
        this.password = password;
    }
}
