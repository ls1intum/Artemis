export class User {
    public id?: number;
    public login?: string;
    public firstName?: string;
    public lastName?: string;
    public email?: string;
    public activated?: Boolean;
    public langKey?: string;
    public authorities?: string[];
    public groups?: string[];
    public createdBy?: string;
    public createdDate?: Date;
    public lastModifiedBy?: string;
    public lastModifiedDate?: Date;
    public password?: string;
    public imageUrl?: string;

    constructor(
        id?: number,
        login?: string,
        firstName?: string,
        lastName?: string,
        email?: string,
        activated?: Boolean,
        langKey?: string,
        authorities?: string[],
        groups?: string[],
        createdBy?: string,
        createdDate?: Date,
        lastModifiedBy?: string,
        lastModifiedDate?: Date,
        password?: string,
        imageUrl?: string,
    ) {
        this.id = id ? id : null;
        this.login = login ? login : null;
        this.firstName = firstName ? firstName : null;
        this.lastName = lastName ? lastName : null;
        this.email = email ? email : null;
        this.activated = activated ? activated : false;
        this.langKey = langKey ? langKey : null;
        this.authorities = authorities ? authorities : null;
        this.groups = groups ? groups : null;
        this.createdBy = createdBy ? createdBy : null;
        this.createdDate = createdDate ? createdDate : null;
        this.lastModifiedBy = lastModifiedBy ? lastModifiedBy : null;
        this.lastModifiedDate = lastModifiedDate ? lastModifiedDate : null;
        this.password = password ? password : null;
        this.imageUrl = imageUrl ? imageUrl : null;
    }
}
