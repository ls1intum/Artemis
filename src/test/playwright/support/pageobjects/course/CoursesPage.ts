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
        await this.page.locator(`#course-${courseId}-header`).click();
        await this.page.waitForURL(/\/exercises/);
    }
}
