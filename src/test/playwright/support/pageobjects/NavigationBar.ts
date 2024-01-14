import { Page } from '@playwright/test';
import { COURSE_BASE } from '../constants';

export class NavigationBar {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openCourseManagement() {
        const responsePromise = this.page.waitForResponse(COURSE_BASE + 'course-management-overview*');
        await this.page.goto('/course-management');
        await responsePromise;
        await this.page.waitForURL('**/course-management**');
    }

    async logout() {
        await this.page.locator('#account-menu').click();
        await this.page.locator('#logout').click();
    }
}
