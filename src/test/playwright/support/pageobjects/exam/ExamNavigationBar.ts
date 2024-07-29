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
        await this.page.getByText(exerciseGroupTitle).nth(0).click();
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
        await this.page.locator('#hand-in-early').click();
    }

    async clickSave() {
        await this.page.locator('#save').click();
    }
}
