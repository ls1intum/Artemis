export class Account {
    public activated: boolean | null;
    public authorities: string[] | null;
    public email: string | null;
    public firstName: string | null;
    public langKey: string | null;
    public lastName: string | null;
    public login: string | null;
    public imageUrl: string | null;

    constructor(activated?: boolean, authorities?: string[], email?: string, firstName?: string, langKey?: string, lastName?: string, login?: string, imageUrl?: string) {
        this.login = login || null;
        this.firstName = firstName || null;
        this.lastName = lastName || null;
        this.email = email || null;
        this.activated = activated || null;
        this.langKey = langKey || null;
        this.authorities = authorities || null;
        this.imageUrl = imageUrl || null;
    }
}
