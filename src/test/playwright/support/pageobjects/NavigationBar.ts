import { Page } from '@playwright/test';
import { COURSE_BASE } from '../constants';

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
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/course-management-overview*`);
        await this.page.goto('/course-management');
        await responsePromise;
        await this.page.waitForURL('**/course-management**');
    }

    /**
     * Logs out via the menu at the top.
     */
    async logout() {
        await this.page.locator('#account-menu').click();
        await this.page.locator('#logout').click();
    }
}
