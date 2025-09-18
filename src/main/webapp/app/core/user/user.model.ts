import { Account } from 'app/core/user/account.model';
import dayjs from 'dayjs/esm';
import { Organization } from 'app/core/shared/entities/organization.model';

export class User extends Account {
    public id?: number;
    public groups?: string[];
    public organizations?: Organization[];
    public createdBy?: string;
    public createdDate?: Date;
    public lastModifiedBy?: string;
    public lastModifiedDate?: Date;
    public visibleRegistrationNumber?: string;
    public password?: string;
    public vcsAccessToken?: string;
    public vcsAccessTokenExpiryDate?: string;
    public externalLLMUsageAccepted?: dayjs.Dayjs;
    public memirisEnabled?: boolean;
    /**
     * True if
     * <ul>
     * <li>No passkey has been registered for this user yet</li>
     * <li>and the passkey feature is enabled</li>
     * <li>and <code>artemis.user-management.passkey.ask-users-to-setup</code> is set to true</li>
     * </ul>
     */
    public askToSetupPasskey?: boolean;

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
        imageUrl?: string,
        vcsAccessToken?: string,
        vcsAccessTokenExpiryDate?: string,
        externalLLMUsageAccepted?: dayjs.Dayjs,
        memirisEnabled?: boolean,
        askToSetupPasskey?: boolean,
    ) {
        super(activated, authorities, email, firstName, langKey, lastName, login, imageUrl);
        this.id = id;
        this.groups = groups;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
        this.lastModifiedBy = lastModifiedBy;
        this.lastModifiedDate = lastModifiedDate;
        this.password = password;
        this.vcsAccessToken = vcsAccessToken;
        this.vcsAccessTokenExpiryDate = vcsAccessTokenExpiryDate;
        this.externalLLMUsageAccepted = externalLLMUsageAccepted;
        this.memirisEnabled = memirisEnabled;
        this.askToSetupPasskey = askToSetupPasskey;
    }
}
/**
 * A DTO representing a user with the minimal information allowed to be seen by other users in a course
 */
export class UserPublicInfoDTO {
    public id?: number;
    public login?: string;

    public name?: string;
    public firstName?: string;
    public lastName?: string;
    public email?: string;
    public imageUrl?: string;
    public isInstructor?: boolean;
    public isEditor?: boolean;
    public isTeachingAssistant?: boolean;
    public isStudent?: boolean;
}

/**
 * A DTO representing a user which contains only the name and login
 */
export class UserNameAndLoginDTO {
    public name?: string;
    public login?: string;
}
