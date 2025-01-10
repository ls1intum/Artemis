import { Page } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the courses page (/courses).
 */
export class CoursesPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openCourseAndFirstExercise(courseId: number) {
        for (let i = 0; i < 10; i++) {
            if (/\/exercises\/\d+/.test(this.page.url())) {
                return;
            } else if (/\/exercises/.test(this.page.url())) {
                await this.page.locator('#test-sidebar-card-medium').click();
            } else {
                await this.page.locator(`#course-${courseId}-header`).click();
            }
            await this.page.waitForTimeout(1000);
        }
    }
}
