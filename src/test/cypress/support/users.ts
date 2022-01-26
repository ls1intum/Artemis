import { Authority } from './../../../main/webapp/app/shared/constants/authority.constants';
import { User } from './../../../main/webapp/app/core/user/user.model';
import { CourseManagementRequests } from './requests/CourseManagementRequests';
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

    private getUserWithId(id: string): CypressCredentials {
        const username = this.getUsernameTemplate().replace(USER_ID_SELECTOR, id);
        const password = this.getPasswordTemplate().replace(USER_ID_SELECTOR, id);
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

    public createRequiredUsers(requests: CourseManagementRequests) {
        this.createUser(0, Authority.USER, requests);
        this.createUser(1, Authority.USER, requests);
        this.createUser(2, Authority.USER, requests);
        this.createUser(3, Authority.INSTRUCTOR, requests);
        this.createUser(4, Authority.TA, requests);
    }

    private createUser(id: number, role: Authority, requests: CourseManagementRequests) {
        const user = this.generateUserWithId(id, role);
        requests.createUser(user).then((request: any) => {
            if (request.status == 400) {
                expect(request.body.errorKey).to.eq('userexists');
                cy.task('debug', 'Cypress user already exists. Skipping user creation!');
            } else {
                expect(request.status).to.eq(201);
                cy.task('debug', 'Created new cypress user!');
            }
        });
    }

    private generateUserWithId(id: number, role: Authority): User {
        const cred = this.getUserWithId(id.toString());
        const firstName = `tester_${id}`;
        const lastName = 'cypress';
        const email = `${firstName}@in.tum.de`;
        return new User(
            undefined,
            cred.username,
            firstName,
            lastName,
            email,
            true,
            undefined,
            [role],
            undefined,
            undefined,
            undefined,
            undefined,
            undefined,
            undefined,
            undefined,
            cred.password,
        );
    }
}

/**
 * Container class for user credentials.
 */
export interface CypressCredentials {
    username: string;
    password: string;
}
