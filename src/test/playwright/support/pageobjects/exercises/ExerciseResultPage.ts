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
        // Reload until the score text is actually rendered inside the result component.
        // Using reloadUntilTextFound instead of reloadUntilFound + separate text check
        // avoids a race where the graded-result element exists but Angular hasn't computed
        // the resultString yet after the last reload.
        const resultScore = this.page.locator('jhi-course-exercise-details #submission-result-graded');
        await Commands.reloadUntilTextFound(this.page, resultScore, `${percentage}%`, 4000, 60000);
    }
}
