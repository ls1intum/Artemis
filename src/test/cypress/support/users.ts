import { BASE_API, GET, USER_ID_SELECTOR } from './constants';

export enum UserRole {
    Instructor = 'ROLE_INSTRUCTOR',
    Tutor = 'ROLE_TA',
    Student = 'ROLE_USER',
}

export const USER_ID = {
    studentOne: 100,
    studentTwo: 102,
    studentThree: 104,
    studentFour: 106,
    instructor: 103,
    tutor: 101,
};

export const USER_ROLE = {
    studentOne: UserRole.Student,
    studentTwo: UserRole.Student,
    studentThree: UserRole.Student,
    studentFour: UserRole.Student,
    instructor: UserRole.Instructor,
    tutor: UserRole.Tutor,
};

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
        return this.getUserWithId(USER_ID.studentOne);
    }

    /**
     * @returns the second testing account with student rights.
     */
    public getStudentTwo(): CypressCredentials {
        return this.getUserWithId(USER_ID.studentTwo);
    }

    /**
     * @returns the third testing account with student rights.
     */
    public getStudentThree(): CypressCredentials {
        return this.getUserWithId(USER_ID.studentThree);
    }

    /**
     * @returns the fourth testing account with student rights.
     */
    public getStudentFour(): CypressCredentials {
        return this.getUserWithId(USER_ID.studentFour);
    }

    /**
     * @returns an instructor account.
     */
    public getInstructor(): CypressCredentials {
        return this.getUserWithId(USER_ID.instructor);
    }

    /**
     * @returns a tutor account.
     */
    public getTutor(): CypressCredentials {
        return this.getUserWithId(USER_ID.tutor);
    }

    public getUserWithId(userId: number): CypressCredentials {
        const username = this.getUsernameTemplate().replace(USER_ID_SELECTOR, userId.toString());
        const password = this.getPasswordTemplate().replace(USER_ID_SELECTOR, userId.toString());
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
    public getAccountInfo(func: (response: any) => void) {
        cy.request({ method: GET, url: `${BASE_API}/public/account`, log: false }).then((response) => {
            func(response.body);
        });
    }

    public getUserInfo(username: string, func: (response: any) => void) {
        cy.request({ method: GET, url: `${BASE_API}/admin/users/${username}`, log: false }).then((response) => {
            func(response.body);
        });
    }
}

// Users
export const users = new CypressUserManagement();
export const admin = users.getAdmin();
export const instructor = users.getInstructor();
export const tutor = users.getTutor();
export const studentOne = users.getStudentOne();
export const studentTwo = users.getStudentTwo();
export const studentThree = users.getStudentThree();
export const studentFour = users.getStudentFour();

/**
 * Container class for user credentials.
 */
export interface CypressCredentials {
    username: string;
    password: string;
}
