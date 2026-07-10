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
        // Under heavy multi-node load the card click occasionally completes without triggering
        // Angular's router (the page stays on /courses). A bare `waitForURL` without a timeout would
        // then hang until the whole (multi-minute) test budget is exhausted — which is exactly how the
        // team git-submission test timed out. Bound the wait and fall back to an explicit navigation so
        // the test recovers instead of stalling. Mirrors CourseManagementPage.openCourse.
        // Scope the match to the course we actually clicked. A broad /\/courses\/\d+\/exercises/ would
        // also "settle" if a stale/wrong-card click under load landed on a different course's exercises
        // page, silently continuing on the wrong course instead of triggering the goto fallback.
        const expectedUrl = new RegExp(`/courses/${courseId}/exercises`);
        const settled = await this.page
            .waitForURL(expectedUrl, { timeout: 15000 })
            .then(() => true)
            .catch(() => false);
        if (!settled) {
            await this.page.goto(`/courses/${courseId}/exercises`);
            await this.page.waitForURL(expectedUrl, { timeout: 30000 });
        }
    }
}
