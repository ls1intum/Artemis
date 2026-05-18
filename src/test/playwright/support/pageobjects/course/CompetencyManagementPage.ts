import { Page } from '@playwright/test';

export class CompetencyManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async goto(courseId: number) {
        // Wait for the initial competency fetch so subsequent steps don't race the page load.
        // The fixture-level `page.goto` wrapper (see `support/fixtures.ts`) already handles
        // Angular bootstrap recovery via reload-on-missing-navbar, and the worker-scoped
        // chunk pre-warm in `support/baseFixtures.ts` ensures the lazy route chunk is
        // already cached — so a single bounded response wait is enough.
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes(`/api/atlas/courses/${courseId}/course-competencies`) && resp.status() < 400, {
            timeout: 30000,
        });
        await this.page.goto(`/course-management/${courseId}/competency-management`);
        await responsePromise;
        const closeButton = this.page.locator('#close-button');
        if (await closeButton.isVisible()) {
            await closeButton.click();
        }
    }
}
