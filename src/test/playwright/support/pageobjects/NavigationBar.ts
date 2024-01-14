import { Page } from '@playwright/test';
import { COURSE_BASE } from '../constants';

export class NavigationBar {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openCourseManagement() {
        const responsePromise = this.page.waitForResponse(COURSE_BASE + 'course-management-overview*');
        const isNavbarCollapsed = await this.page.locator('#navbarResponsive').getAttribute('ng-reflect-collapsed');
        console.log(`Navbar is ${isNavbarCollapsed ? 'collapsed' : 'not collapsed'}`);
        const toggler = this.page.locator('.toggler');
        if (isNavbarCollapsed && (await toggler.isVisible())) {
            console.log('Expanding the navbar...');
            await toggler.click();
        }
        await this.page.locator('#course-admin-menu').click();
        // await this.page.goto('/course-management');
        await responsePromise;
        await this.page.waitForURL('**/course-management**');
    }

    async logout() {
        await this.page.locator('#account-menu').click();
        await this.page.locator('#logout').click();
    }
}
