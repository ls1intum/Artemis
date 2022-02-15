import { BASE_API, GET, USER_ID_SELECTOR } from './constants';

/**
 * Class to encompass user management logic for cypress tests.
 */
export class CypressUserManagement {
    /**
     * @returns admin credentials.
     */
    public getAdmin(): CypressCredentials {
        const adminUsername = Cypress.env('adminUsername') ?? 'admin';
        const adminPassword = Cypress.env('adminPassword') ?? 'admin';
        return { username: adminUsername, password: adminPassword };
    }

    /**
     * @returns the first testing account with student rights.
     */
    public getStudentOne(): CypressCredentials {
        return this.getUserWithId('100');
    }

    /**
     * @returns the second testing account with student rights.
     */
    public getStudentTwo(): CypressCredentials {
        return this.getUserWithId('102');
    }

    /**
     * @returns the third testing account with student rights.
     */
    public getStudentThree(): CypressCredentials {
        return this.getUserWithId('104');
    }

    /**
     * @returns an instructor account.
     */
    public getInstructor(): CypressCredentials {
        return this.getUserWithId('103');
    }

    /**
     * @returns a tutor account.
     */
    public getTutor(): CypressCredentials {
        return this.getUserWithId('101');
    }

    private getUserWithId(userId: string): CypressCredentials {
        const username = this.getUsernameTemplate().replace(USER_ID_SELECTOR, userId);
        const password = this.getPasswordTemplate().replace(USER_ID_SELECTOR, userId);
        return { username, password };
    }

    /**
     * @returns the username template.
     */
    private getUsernameTemplate(): string {
        return Cypress.env('username') ?? 'user_' + USER_ID_SELECTOR;
    }

    /**
     * @returns the password template.
     */
    private getPasswordTemplate(): string {
        return Cypress.env('password') ?? 'password_' + USER_ID_SELECTOR;
    }

    /**
     * Provides the entire account info for the user that is currently logged in
     * Use like this: artemis.users.getAccountInfo((account) => { someFunction(account); });
     * */
    public getAccountInfo(func: Function) {
        cy.request({ method: GET, url: BASE_API + 'account', log: false }).then((response) => {
            func(response.body);
        });
    }
}

/**
 * Container class for user credentials.
 */
export interface CypressCredentials {
    username: string;
    password: string;
}
