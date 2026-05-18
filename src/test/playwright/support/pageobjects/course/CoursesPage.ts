import { Page } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the courses page (/courses).
 */
export class CoursesPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openCourse(courseId: number) {
        const header = this.page.locator(`#course-${courseId}-header`);
        // The courses API may be slow under parallel test load.
        // Wait explicitly for the course card to appear before clicking.
        await header.waitFor({ state: 'visible', timeout: 30000 });
        await header.click();
        await this.page.waitForURL(/\/exercises/);
    }
}
