import { Account } from '../../core';

export class User extends Account {
    public id: number;
    public groups: string[];
    public createdBy: string;
    public createdDate: Date;
    public lastModifiedBy: string;
    public lastModifiedDate: Date;
    public password: string;

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
        password?: string,
        imageUrl?: string
    ) {
        super(activated, authorities, email, firstName, langKey, lastName, login, imageUrl);
        this.id = id ? id : null;
        this.groups = groups ? groups : null;
        this.createdBy = createdBy ? createdBy : null;
        this.createdDate = createdDate ? createdDate : null;
        this.lastModifiedBy = lastModifiedBy ? lastModifiedBy : null;
        this.lastModifiedDate = lastModifiedDate ? lastModifiedDate : null;
        this.password = password ? password : null;
    }
}
