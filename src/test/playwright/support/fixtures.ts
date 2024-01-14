import { test as base } from '@playwright/test';
import { LoginPage } from './pageobjects/LoginPage';
import { UserCredentials } from './users';
import { NavigationBar } from './pageobjects/NavigationBar';
import { CourseManagementAPIRequests } from './requests/CourseManagementAPIRequests';
import { CourseManagementPage } from './pageobjects/course/CourseManagementPage';
import { CourseCreationPage } from './pageobjects/course/CourseCreationPage';
import { UserManagementAPIRequests } from './requests/UserManagementAPIRequests';
import { Commands } from './commands';

type ArtemisCommands = {
    login: (credentials: UserCredentials, url?: string) => Promise<void>;
};

type ArtemisPageObjects = {
    loginPage: LoginPage;
    navigationBar: NavigationBar;
    courseManagement: CourseManagementPage;
    courseCreation: CourseCreationPage;
};

export class ArtemisRequests {
    courseManagementAPIRequests: CourseManagementAPIRequests;
    userManagementAPIRequests: UserManagementAPIRequests;
}

export const test = base.extend<ArtemisPageObjects & ArtemisCommands & ArtemisRequests>({
    loginPage: async ({ page }, use) => {
        await use(new LoginPage(page));
    },
    login: async ({ page }, use) => {
        await use(async (credentials: UserCredentials, url?: string) => {
            await Commands.login(page, credentials, url);
        });
    },
    navigationBar: async ({ page }, use) => {
        await use(new NavigationBar(page));
    },
    courseManagementAPIRequests: async ({ page }, use) => {
        await use(new CourseManagementAPIRequests(page));
    },
    // eslint-disable-next-line no-empty-pattern
    userManagementAPIRequests: async ({ page }, use) => {
        await use(new UserManagementAPIRequests(page));
    },
    courseManagement: async ({ page }, use) => {
        await use(new CourseManagementPage(page));
    },
    courseCreation: async ({ page }, use) => {
        await use(new CourseCreationPage(page));
    },
});
