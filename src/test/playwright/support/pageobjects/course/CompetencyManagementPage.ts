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

    /**
     * Opens the detail page of the competency with the given title from the management list.
     *
     * Navigates to the competency management page and clicks the competency's link. Robust against the multi-node lag
     * where a freshly created/updated competency link is not yet rendered on the routed node: if the link does not appear,
     * it reloads the management page (re-fetching the competency list) before clicking. Without this, clicking a link that
     * only a reload would surface auto-waits until the whole test times out.
     *
     * @param courseId - The ID of the course the competency belongs to.
     * @param title - The exact title of the competency to open.
     */
    async openCompetencyDetail(courseId: number, title: string) {
        const link = this.page.getByRole('link', { name: title });
        for (let attempt = 0; attempt < 3; attempt++) {
            // goto() already waits for the competency-list GET (and reloads once if it is slow); navigating fresh on each
            // attempt re-renders the list so a link that has not surfaced on the routed node yet appears on a later attempt.
            await this.goto(courseId);
            try {
                await link.waitFor({ state: 'visible', timeout: 10000 });
                await link.click();
                return;
            } catch (error) {
                if (attempt === 2) {
                    throw error;
                }
            }
        }
    }
}
