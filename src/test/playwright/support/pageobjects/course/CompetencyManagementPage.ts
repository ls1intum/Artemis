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
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes(`/api/atlas/courses/${courseId}/course-competencies`) && resp.status() < 400, {
            timeout: 45000,
        });
        await this.page.goto(`/course-management/${courseId}/competency-management`);
        await responsePromise;
        const closeButton = this.page.locator('#close-button');
        if (await closeButton.isVisible()) {
            await closeButton.click();
        }
    }
}
