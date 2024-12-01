import { Page } from '@playwright/test';

export class EditExamPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openExerciseGroups() {
        await this.page.locator(`#exercises-button-groups-table`).click();
    }
}
