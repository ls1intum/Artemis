import { BASE_API } from './constants';
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
    public getAdmin(): UserCredentials {
        return admin;
    }

    public getStudentOne(): UserCredentials {
        return studentOne;
    }

    public getStudentTwo(): UserCredentials {
        return studentTwo;
    }

    public getStudentThree(): UserCredentials {
        return studentThree;
    }

    public getStudentFour(): UserCredentials {
        return studentFour;
    }

    public getInstructor(): UserCredentials {
        return instructor;
    }

    public getTutor(): UserCredentials {
        return tutor;
    }

    public getUserWithId(userId: number): UserCredentials {
        return { username: `artemis_test_user_${userId}`, password: `artemis_test_user_${userId}` };
    }

    public async getAccountInfo(request: APIRequestContext): Promise<Account> {
        const response = await request.get(`${BASE_API}/core/public/account`);
        return response.json();
    }

    public async getUserInfo(username: string, page: Page): Promise<User> {
        const response = await page.request.get(`${BASE_API}/core/admin/users/${username}`);
        return response.json();
    }

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

// Fixed user credentials — passwords equal usernames
export const admin: UserCredentials = { username: 'artemis_admin', password: 'artemis_admin' };
export const studentOne: UserCredentials = { username: 'artemis_test_user_1', password: 'artemis_test_user_1' };
export const studentTwo: UserCredentials = { username: 'artemis_test_user_2', password: 'artemis_test_user_2' };
export const studentThree: UserCredentials = { username: 'artemis_test_user_3', password: 'artemis_test_user_3' };
export const studentFour: UserCredentials = { username: 'artemis_test_user_4', password: 'artemis_test_user_4' };
export const tutor: UserCredentials = { username: 'artemis_test_user_6', password: 'artemis_test_user_6' };
export const instructor: UserCredentials = { username: 'artemis_test_user_16', password: 'artemis_test_user_16' };
