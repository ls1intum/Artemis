import { Page } from '@playwright/test';
import { retry } from '../utils';

/**
 * A class which encapsulates UI selectors and actions for the navigation bar at the top.
 */
export class NavigationBar {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Opens the course management page via the menu at the top and waits until it is loaded.
     */
    async openCourseManagement() {
        await retry(async () => {
            await this.page.goto('/course-management');
            await this.page.locator('#course-page-heading').waitFor({ timeout: 3_000 });
            await this.page.waitForURL('**/course-management**');
        }, 'Could not open course management page ' + this.page.url());
    }

    /**
     * Logs out via the menu at the top.
     */
    async logout() {
        await this.page.locator('#account-menu').click();
        await this.page.locator('#logout').click();
    }
}
