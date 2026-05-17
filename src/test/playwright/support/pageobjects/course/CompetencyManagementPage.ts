import { Page } from '@playwright/test';

export class CompetencyManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async goto(courseId: number) {
        // Opportunistically wait for the initial competency fetch so subsequent steps are not
        // racing the page load. The wait is bounded (15s) and `.catch`ed so it never blows the
        // @fast 60s budget when tests call `goto` twice — when the response truly stalls, the
        // caller's own auto-waiting locator assertion will surface the real failure with a
        // clearer message than a generic `waitForResponse: Timeout` here.
        //
        // The `page.goto` fixture wrapper (see `support/fixtures.ts`) already handles the case
        // where Angular fails to bootstrap the route component on the first navigation by
        // reloading once, so by the time `goto` returns the page is hydrated.
        const responsePromise = this.page
            .waitForResponse((resp) => resp.url().includes(`/api/atlas/courses/${courseId}/course-competencies`) && resp.status() < 400, { timeout: 15000 })
            .catch(() => undefined);
        await this.page.goto(`/course-management/${courseId}/competency-management`);
        await responsePromise;
        const closeButton = this.page.locator('#close-button');
        if (await closeButton.isVisible()) {
            await closeButton.click();
        }
    }
}
