import { Page } from '@playwright/test';

export class CompetencyManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async goto(courseId: number) {
        // Wait for both the SPA route mount AND the initial competency fetch so subsequent steps
        // are not racing the page load. Under parallel CI load the response can take >15s; loosen
        // the status assertion (any 2xx/3xx, not strictly 200) and bump the timeout accordingly.
        //
        // If `waitForResponse` times out, it usually means Angular failed to bootstrap the
        // route component — the API request that would have produced the response was never
        // made. Reload once and retry: a fresh chunk fetch typically recovers from the
        // multi-node-load Angular bootstrap race.
        const url = `/course-management/${courseId}/competency-management`;
        const responsePredicate = (resp: { url(): string; status(): number }) => resp.url().includes(`/api/atlas/courses/${courseId}/course-competencies`) && resp.status() < 400;

        const waitOnce = async () => this.page.waitForResponse(responsePredicate, { timeout: 30_000 });

        const firstWait = waitOnce();
        await this.page.goto(url);
        try {
            await firstWait;
        } catch {
            const secondWait = waitOnce();
            await this.page.reload();
            await secondWait;
        }
        const closeButton = this.page.locator('#close-button');
        if (await closeButton.isVisible()) {
            await closeButton.click();
        }
    }
}
