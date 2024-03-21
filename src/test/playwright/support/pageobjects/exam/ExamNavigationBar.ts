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
     * Opens the exercise at the specified index.
     * @param index 0-based index
     */
    async openExerciseAtIndex(index: number) {
        await this.page.locator('#exam-exercise-' + index).click();
    }

    async openExerciseOverview() {
        await this.page.locator('.exam-navigation .navigation-item.overview').click();
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
