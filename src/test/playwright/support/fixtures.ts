import { test as base } from '@playwright/test';
import { LoginPage } from './pageobjects/LoginPage';
import { UserCredentials } from './users';
import { NavigationBar } from './pageobjects/NavigationBar';
import { CourseManagementAPIRequests } from './requests/CourseManagementAPIRequests';
import { CourseManagementPage } from './pageobjects/course/CourseManagementPage';
import { CourseCreationPage } from './pageobjects/course/CourseCreationPage';
import { UserManagementAPIRequests } from './requests/UserManagementAPIRequests';

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
    page: async ({ context }, use) => {
        const page = await context.newPage();
        await use(page);
    },
    loginPage: async ({ page }, use) => {
        await use(new LoginPage(page));
    },
    login: async ({ page, loginPage, navigationBar }, use) => {
        await use(async (credentials: UserCredentials, url?: string) => {
            await page.goto('/');
            const isLogin = await page.locator('#username').isVisible();

            if (!isLogin) {
                await navigationBar.logout();
            }

            await loginPage.login(credentials);
            await page.waitForURL('**/courses**');

            if (url) {
                await page.goto(url);
            }
            // await Commands.login(page, credentials, url);
        });
    },
    navigationBar: async ({ page }, use) => {
        await use(new NavigationBar(page));
    },
    courseManagementAPIRequests: async ({ page }, use) => {
        await use(new CourseManagementAPIRequests(page));
    },
    // eslint-disable-next-line no-empty-pattern
    userManagementAPIRequests: async ({}, use) => {
        await use(new UserManagementAPIRequests());
    },
    courseManagement: async ({ page }, use) => {
        await use(new CourseManagementPage(page));
    },
    courseCreation: async ({ page }, use) => {
        await use(new CourseCreationPage(page));
    },
    // context: async ({ context }, use) => {
    //     await context.route('**/*', (route, req) => {
    //         const options = {
    //             uri: req.url(),
    //             method: req.method(),
    //             headers: req.headers(),
    //             body: req.postDataBuffer(),
    //             timeout: 10000,
    //             followRedirect: false,
    //             agentOptions: {
    //                 ca: fs.readFileSync('./certs/rootCA.pem'),
    //                 cert: fs.readFileSync('./certs/artemis-nginx+4.pem'),
    //                 key: fs.readFileSync('./certs/artemis-nginx+4-key.pem'),
    //             },
    //         };
    //         let firstTry = true;
    //         const handler = (err: { code: any }, resp: { statusCode: any; headers: any }, data: any) => {
    //             if (err) {
    //                 if (firstTry) {
    //                     firstTry = false;
    //                     return request(options, handler);
    //                 }
    //                 // console.error(`Unable to call ${options.uri}`, err.code, err);
    //                 return route.abort();
    //             } else {
    //                 return route.fulfill({
    //                     status: resp.statusCode,
    //                     headers: resp.headers,
    //                     body: data,
    //                 });
    //             }
    //         };
    //         return request(options, handler);
    //     });
    //
    //     await use(context);
    // },
});
