import { GuidedTourSetting } from 'app/guided-tour/guided-tour-setting.model';

export class Account {
    public activated?: boolean;
    public authorities?: string[];
    public login?: string;
    public email?: string;
    public name?: string;
    public isInternal: boolean;
    public firstName?: string;
    public lastName?: string;
    public langKey?: string;
    public imageUrl?: string;
    public guidedTourSettings: GuidedTourSetting[];

    constructor(
        activated?: boolean,
        authorities?: string[],
        email?: string,
        firstName?: string,
        langKey?: string,
        lastName?: string,
        login?: string,
        imageUrl?: string,
        guidedTourSettings?: GuidedTourSetting[],
    ) {
        this.login = login;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.activated = activated;
        this.langKey = langKey;
        this.authorities = authorities;
        this.imageUrl = imageUrl;
        this.guidedTourSettings = guidedTourSettings || [];
    }
}
