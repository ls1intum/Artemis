import { Page } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the navigation bar in an open exam.
 */
export class ExamNavigationBar {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Opens the exercise with the given group title
     * @param exerciseGroupTitle
     */
    async openOrSaveExerciseByTitle(exerciseGroupTitle: string) {
        const exerciseLink = this.page.getByText(exerciseGroupTitle).nth(0);
        await exerciseLink.waitFor({ state: 'visible', timeout: 30000 });
        await exerciseLink.click();
        // Wait for page transition to complete
        await this.page.waitForLoadState('domcontentloaded');
    }

    async openFromOverviewByTitle(exerciseGroupTitle: string) {
        await this.page.getByText(exerciseGroupTitle).locator('xpath=ancestor-or-self::a').click();
    }

    async openOverview() {
        await this.page.getByText('Overview').nth(0).click();
    }

    /**
     * Presses the hand in early button in the navigation bar.
     */
    async handInEarly() {
        await this.page.locator('#hand-in-early').click({ timeout: 30000 });
    }

    async clickSave() {
        await this.page.locator('#save').click();
    }
}
