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
     * Opens the exercise with the given group title.
     *
     * The exam-conduction view is reached via a button click that triggers an in-app
     * route transition (not a `page.goto`), so the fixture-level render-check wrapper
     * does not get a chance to run on it. Under heavy multi-node CI load the conduction
     * page occasionally finishes rendering only after the static app shell + footer
     * (the same lazy-chunk race the goto wrapper guards against), leaving the navbar
     * exercise group titles missing for 30s+. When the first 30s visibility wait fails
     * we reload once and try again — typically recovers within one round trip.
     */
    async openOrSaveExerciseByTitle(exerciseGroupTitle: string) {
        const exerciseLink = this.page.getByText(exerciseGroupTitle).nth(0);
        const visibleWithin = async (timeout: number): Promise<boolean> =>
            exerciseLink
                .waitFor({ state: 'visible', timeout })
                .then(() => true)
                .catch(() => false);
        if (!(await visibleWithin(30000))) {
            await this.page.reload();
            await this.page.waitForLoadState('load');
            await exerciseLink.waitFor({ state: 'visible', timeout: 30000 });
        }
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
