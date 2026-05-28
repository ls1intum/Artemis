import { Page } from '@playwright/test';

export class CompetencyManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async goto(courseId: number) {
        // Wait for the initial competency fetch so subsequent steps don't race the page load.
        // Under heavy multi-node CI load the GET /course-competencies has been observed to
        // take >30s; reload once before giving up. The fixture-level `page.goto` wrapper
        // (see `support/fixtures.ts`) handles Angular bootstrap recovery; here we guard
        // against the slower-than-expected backend response specifically.
        const expectedResponse = (resp: { url: () => string; status: () => number }): boolean =>
            resp.url().includes(`/api/atlas/courses/${courseId}/course-competencies`) && resp.status() < 400;
        let responsePromise = this.page.waitForResponse(expectedResponse, { timeout: 30000 });
        await this.page.goto(`/course-management/${courseId}/competency-management`);
        const settled = await responsePromise.then(() => true).catch(() => false);
        if (!settled) {
            responsePromise = this.page.waitForResponse(expectedResponse, { timeout: 30000 });
            await this.page.reload();
            await responsePromise;
        }
        const closeButton = this.page.locator('#close-button');
        if (await closeButton.isVisible()) {
            await closeButton.click();
        }
    }
}
