export class Account {
    public activated: boolean;
    public authorities: string[];
    public email: string;
    public firstName: string;
    public langKey: string;
    public lastName: string;
    public login: string;
    public imageUrl: string;

    constructor(
        activated?: boolean,
        authorities?: string[],
        email?: string,
        firstName?: string,
        langKey?: string,
        lastName?: string,
        login?: string,
        imageUrl?: string
    ) {
        this.login = login ? login : null;
        this.firstName = firstName ? firstName : null;
        this.lastName = lastName ? lastName : null;
        this.email = email ? email : null;
        this.activated = activated ? activated : false;
        this.langKey = langKey ? langKey : null;
        this.authorities = authorities ? authorities : null;
        this.imageUrl = imageUrl ? imageUrl : null;
    }
}
