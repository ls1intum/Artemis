import { Page } from '@playwright/test';

export class ProgrammingExerciseOverviewPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Retrieves the Locator for the exercise details bar.
     */
    getExerciseDetails() {
        return this.page.locator('.tab-bar-exercise-details');
    }
}
