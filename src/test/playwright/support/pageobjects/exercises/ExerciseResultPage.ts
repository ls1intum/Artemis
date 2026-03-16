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
        // The score percentage appears in the exercise headers information box after Angular's
        // ngOnChanges fires on the headers component with the updated participation/result data.
        // Under load, there can be a delay between the graded result element appearing and the
        // score text rendering, so use a generous timeout instead of the default 5s.
        await expect(this.page.locator('#exercise-headers-information').getByText(`${percentage}%`)).toBeVisible({ timeout: 30000 });
    }

    async clickOpenExercise(exerciseId: number) {
        await this.page.locator(`#open-exercise-${exerciseId}`).click();
    }

    async clickOpenExerciseAndAwaitRatingResponse(exerciseId: number) {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/assessment/results/*/rating`);
        await this.clickOpenExercise(exerciseId);
        await responsePromise;
    }

    async clickOpenCodeEditor(exerciseId: number) {
        await this.page.locator(`#open-exercise-${exerciseId}`).click();
    }
}
