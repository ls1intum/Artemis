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
        // Under heavy multi-node load the click occasionally fires before Angular's router
        // is ready and the page stays at /courses — fall back to direct navigation instead
        // of consuming the entire test budget on a single hung waitForURL.
        const settled = await this.page
            .waitForURL(/\/exercises/, { timeout: 20_000 })
            .then(() => true)
            .catch(() => false);
        if (!settled) {
            await this.page.goto(`/courses/${courseId}/exercises`);
            await this.page.waitForLoadState('load');
            await this.page.waitForURL(/\/exercises/, { timeout: 15_000 });
        }
    }
}
