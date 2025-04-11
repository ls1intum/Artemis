
export class Account {
    public activated?: boolean;
    public authorities?: string[];
    public login?: string;
    public email?: string;
    public name?: string;
    public internal: boolean;
    public firstName?: string;
    public lastName?: string;
    public langKey?: string;
    public imageUrl?: string;

    constructor(
        activated?: boolean,
        authorities?: string[],
        email?: string,
        firstName?: string,
        langKey?: string,
        lastName?: string,
        login?: string,
        imageUrl?: string,
    ) {
        this.login = login;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.activated = activated;
        this.langKey = langKey;
        this.authorities = authorities;
        this.imageUrl = imageUrl;
    }
}
