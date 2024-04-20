import { Page } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the text exercise example submissions page.
 */
export class TextExerciseExampleSubmissionsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async clickCreateExampleSubmission() {
        await this.page.locator('#create-example-submission').click();
    }
}
