import { BASE_API, USER_ID_SELECTOR } from './constants';
import { User } from 'app/core/user/user.model';
import { APIRequestContext, Page } from '@playwright/test';
import { Account } from 'app/core/user/account.model';
import { NavigationBar } from './pageobjects/NavigationBar';
import { CourseManagementPage } from './pageobjects/course/CourseManagementPage';

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
        const adminUsername = process.env.ADMIN_USERNAME ?? 'admin';
        const adminPassword = process.env.ADMIN_PASSWORD ?? 'admin';
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
        return (process.env.PLAYWRIGHT_USERNAME_TEMPLATE ?? 'user_') + USER_ID_SELECTOR;
    }

    /**
     * @returns the password template.
     */
    private getPasswordTemplate(): string {
        return (process.env.PLAYWRIGHT_PASSWORD_TEMPLATE ?? 'password_') + USER_ID_SELECTOR;
    }

    /**
     * Provides the entire account info for the user that is currently logged in
     * Use like this: artemis.users.getAccountInfo((account) => { someFunction(account); });
     * */
    public async getAccountInfo(request: APIRequestContext): Promise<Account> {
        const response = await request.get(`${BASE_API}/core/public/account`);
        return response.json();
    }

    public async getUserInfo(username: string, page: Page): Promise<User> {
        const response = await page.request.get(`${BASE_API}/core/admin/users/${username}`);
        return response.json();
    }

    /**
     * Creates a user in a course with the specified role. This method takes care of navigating to the course management page, no prerequisites for a previous page state are required.
     *
     * @param courseId for which the user should be created
     * @param userCredentials of the user to be created
     * @param role of the user to be created
     * @param navigationBar to navigate to the course management page
     * @param courseManagement to perform the user creation
     */
    public static async createUserInCourse(
        courseId: number,
        userCredentials: UserCredentials,
        role: UserRole,
        navigationBar: NavigationBar,
        courseManagement: CourseManagementPage,
    ): Promise<void> {
        await navigationBar.openCourseManagement();
        await courseManagement.openCourse(courseId);

        switch (role) {
            case UserRole.Student:
                await courseManagement.addStudentToCourse(userCredentials);
                break;
            case UserRole.Tutor:
                await courseManagement.addTutorToCourse(userCredentials);
                break;
            case UserRole.Instructor:
                await courseManagement.addInstructorToCourse(userCredentials);
                break;
            default:
                throw new Error(`Cannot create user, unsupported role: ${role}`);
        }
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
