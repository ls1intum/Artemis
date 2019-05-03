import { Account } from '../../core';
import { Moment } from 'moment';

export class User extends Account {
    public id: number | null;
    public groups: string[] | null;
    public createdBy: string | null;
    public createdDate: Date | null;
    public lastModifiedBy: string | null;
    public lastModifiedDate: Date | null;
    public lastNotificationRead: Moment | null;
    public password: string | null;

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
        super(activated || null, authorities || null, email || null, firstName || null, langKey || null, lastName || null, login || null, imageUrl || null);
        this.id = id ? id : null;
        this.groups = groups ? groups : null;
        this.createdBy = createdBy ? createdBy : null;
        this.createdDate = createdDate ? createdDate : null;
        this.lastModifiedBy = lastModifiedBy ? lastModifiedBy : null;
        this.lastModifiedDate = lastModifiedDate ? lastModifiedDate : null;
        this.lastNotificationRead = lastNotificationRead ? lastNotificationRead : null;
        this.password = password ? password : null;
    }
}
