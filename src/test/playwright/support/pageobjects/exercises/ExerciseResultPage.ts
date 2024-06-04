import { Page } from 'playwright';
import { expect } from '@playwright/test';
import { Commands } from '../../commands';
import { BASE_API } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the exercise result page.
 */
export class ExerciseResultPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async shouldShowProblemStatement(problemStatement: string) {
        const problemStatementField = this.page.locator('#problem-statement');
        await expect(problemStatementField).toContainText(problemStatement);
        await expect(problemStatementField).toBeVisible();
    }

    async shouldShowExerciseTitle(title: string) {
        await expect(this.page.locator('#exercise-header')).toContainText(title, { timeout: 10000 });
        await expect(this.page.locator('#exercise-header')).toBeVisible();
    }

    async shouldShowScore(percentage: number) {
        await Commands.reloadUntilFound(this.page, this.page.locator('jhi-course-exercise-details #submission-result-graded'), 4000, 60000);
        // TODO this is not the correct selector
        await expect(this.page.locator('.tab-bar-exercise-details').getByText(`${percentage}%`)).toBeVisible();
    }

    async clickOpenExercise(exerciseId: number) {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/results/*/rating`);
        await this.page.locator(`#open-exercise-${exerciseId}`).click();
        await responsePromise;
    }

    async clickOpenCodeEditor(exerciseId: number) {
        await this.page.locator(`#open-exercise-${exerciseId}`).click();
    }
}
