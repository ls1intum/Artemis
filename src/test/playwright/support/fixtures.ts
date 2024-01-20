import { test as base } from '@playwright/test';
import { LoginPage } from './pageobjects/LoginPage';
import { UserCredentials } from './users';
import { NavigationBar } from './pageobjects/NavigationBar';
import { CourseManagementAPIRequests } from './requests/CourseManagementAPIRequests';
import { CourseManagementPage } from './pageobjects/course/CourseManagementPage';
import { CourseCreationPage } from './pageobjects/course/CourseCreationPage';
import { UserManagementAPIRequests } from './requests/UserManagementAPIRequests';
import { Commands } from './commands';
import { ExerciseAPIRequests } from './requests/ExerciseAPIRequests';
import { CourseOverviewPage } from './pageobjects/course/CourseOverviewPage';
import { CourseMessagesPage } from './pageobjects/course/CourseMessagesPage';
import { ExamAPIRequests } from './requests/ExamAPIRequests';
import { CommunicationAPIRequests } from './requests/CommunicationAPIRequests';

/*
 * Define custom types for fixtures
 */

type ArtemisCommands = {
    login: (credentials: UserCredentials, url?: string) => Promise<void>;
};

type ArtemisPageObjects = {
    loginPage: LoginPage;
    navigationBar: NavigationBar;
    courseManagement: CourseManagementPage;
    courseCreation: CourseCreationPage;
    courseOverview: CourseOverviewPage;
    courseMessages: CourseMessagesPage;
};

type ArtemisRequests = {
    courseManagementAPIRequests: CourseManagementAPIRequests;
    userManagementAPIRequests: UserManagementAPIRequests;
    exerciseAPIRequests: ExerciseAPIRequests;
    examAPIRequests: ExamAPIRequests;
    communicationAPIRequests: CommunicationAPIRequests;
};

/**
 * Custom test object extended to use Artemis related fixtures.
 */
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
    courseManagement: async ({ page }, use) => {
        await use(new CourseManagementPage(page));
    },
    courseCreation: async ({ page }, use) => {
        await use(new CourseCreationPage(page));
    },
    courseOverview: async ({ page }, use) => {
        await use(new CourseOverviewPage(page));
    },
    courseMessages: async ({ page }, use) => {
        await use(new CourseMessagesPage(page));
    },
    courseManagementAPIRequests: async ({ page }, use) => {
        await use(new CourseManagementAPIRequests(page));
    },
    userManagementAPIRequests: async ({ page }, use) => {
        await use(new UserManagementAPIRequests(page));
    },
    exerciseAPIRequests: async ({ page }, use) => {
        await use(new ExerciseAPIRequests(page));
    },
    examAPIRequests: async ({ page }, use) => {
        await use(new ExamAPIRequests(page));
    },
    communicationAPIRequests: async ({ page }, use) => {
        await use(new CommunicationAPIRequests(page));
    },
});
