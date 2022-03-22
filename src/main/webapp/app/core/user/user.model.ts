import { Account } from 'app/core/user/account.model';
import dayjs from 'dayjs/esm';
import { Organization } from 'app/entities/organization.model';

export class User extends Account {
    public id?: number;
    public groups?: string[];
    public organizations?: Organization[];
    public createdBy?: string;
    public createdDate?: Date;
    public lastModifiedBy?: string;
    public lastModifiedDate?: Date;
    public lastNotificationRead?: dayjs.Dayjs;
    public registrationNumber?: string;
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
        organizations?: Organization[],
        createdBy?: string,
        createdDate?: Date,
        lastModifiedBy?: string,
        lastModifiedDate?: Date,
        lastNotificationRead?: dayjs.Dayjs,
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
