import { USER_ID_SELECTOR } from './constants';

export interface UserCredentials {
    username: string;
    password: string;
}

export const USER_ID = {
    studentOne: 1,
    studentTwo: 2,
    studentThree: 3,
    studentFour: 4,
    instructor: 16,
    tutor: 6,
};

export enum UserRole {
    Instructor = 'ROLE_INSTRUCTOR',
    Tutor = 'ROLE_TA',
    Student = 'ROLE_USER',
}

export const USER_ROLE = {
    studentOne: UserRole.Student,
    studentTwo: UserRole.Student,
    studentThree: UserRole.Student,
    studentFour: UserRole.Student,
    instructor: UserRole.Instructor,
    tutor: UserRole.Tutor,
};

export class PlaywrightUserManagement {
    /**
     * @returns admin credentials.
     */
    public getAdmin(): UserCredentials {
        const adminUsername = process.env.adminUsername ?? 'admin';
        const adminPassword = process.env.adminPassword ?? 'admin';
        return { username: adminUsername, password: adminPassword };
    }

    /**
     * @returns the first testing account with student rights.
     */
    public getStudentOne(): UserCredentials {
        return this.getUserWithId(USER_ID.studentOne);
    }

    /**
     * @returns the second testing account with student rights.
     */
    public getStudentTwo(): UserCredentials {
        return this.getUserWithId(USER_ID.studentTwo);
    }

    /**
     * @returns the third testing account with student rights.
     */
    public getStudentThree(): UserCredentials {
        return this.getUserWithId(USER_ID.studentThree);
    }

    /**
     * @returns the fourth testing account with student rights.
     */
    public getStudentFour(): UserCredentials {
        return this.getUserWithId(USER_ID.studentFour);
    }

    /**
     * @returns an instructor account.
     */
    public getInstructor(): UserCredentials {
        return this.getUserWithId(USER_ID.instructor);
    }

    /**
     * @returns a tutor account.
     */
    public getTutor(): UserCredentials {
        return this.getUserWithId(USER_ID.tutor);
    }

    public getUserWithId(userId: number): UserCredentials {
        const username = this.getUsernameTemplate().replace(USER_ID_SELECTOR, userId.toString());
        const password = this.getPasswordTemplate().replace(USER_ID_SELECTOR, userId.toString());
        return { username, password };
    }

    /**
     * @returns the username template.
     */
    private getUsernameTemplate(): string {
        return process.env.username ?? 'user_' + USER_ID_SELECTOR;
    }

    /**
     * @returns the password template.
     */
    private getPasswordTemplate(): string {
        return process.env.password ?? 'password_' + USER_ID_SELECTOR;
    }
}

export const users = new PlaywrightUserManagement();
export const admin = users.getAdmin();
export const instructor = users.getInstructor();
export const tutor = users.getTutor();
export const studentOne = users.getStudentOne();
export const studentTwo = users.getStudentTwo();
export const studentThree = users.getStudentThree();
export const studentFour = users.getStudentFour();
