import { Page } from '@playwright/test';

export class CompetencyManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async goto(courseId: number) {
        await this.page.goto(`/course-management/${courseId}/competency-management`);
        const closeButton = this.page.locator('#close-button');
        await this.page.waitForLoadState('networkidle');
        if (await closeButton.isVisible()) {
            await closeButton.click();
        }
    }
}
