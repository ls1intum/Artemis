import { Page } from '@playwright/test';

export class CompetencyManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async goto(courseId: number) {
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes(`/api/atlas/courses/${courseId}/course-competencies`) && resp.status() === 200, {
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
