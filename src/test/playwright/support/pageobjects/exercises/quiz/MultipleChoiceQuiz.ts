import { getExercise } from '../../../utils';
import { Page, expect } from '@playwright/test';

export class MultipleChoiceQuiz {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async tickAnswerOption(exerciseID: number, optionNumber: number, quizQuestionId?: number) {
        let scope = getExercise(this.page, exerciseID);
        if (quizQuestionId != null) {
            scope = scope.locator('#question' + quizQuestionId);
        }
        const answerOption = scope.locator('#answer-option-' + optionNumber);
        // Wait for the option's icon (.selection > fa-icon) to render before clicking. The
        // outer answer-option div can be visible while the inner icon is still painting and
        // the (click) handler that toggles selection is wired only after the component's
        // first change-detection cycle — clicking too early lands on a static div with no
        // handler and the toggle silently no-ops.
        await answerOption.waitFor({ state: 'visible', timeout: 30_000 });
        await answerOption.locator('.selection fa-icon').first().waitFor({ state: 'visible', timeout: 30_000 });
        // Click the parent answer-option div (which owns the toggleSelection handler)
        // rather than the inner .selection icon — clicking the icon child works when
        // events bubble but fails when the icon hasn't fully painted yet.
        await answerOption.click();
    }

    /**
     * Submits the quiz. Waits for the submit button to be ENABLED before clicking — under
     * heavy multi-node load the Angular change detection can run before the click registers
     * an answer selection, leaving the submit button disabled and the subsequent click
     * auto-wait stuck against a disabled element until the test timeout fires.
     */
    async submit() {
        const submitButton = this.page.locator('#submit-exercise, #submit-exercise-popover, #submit-quiz').first();
        await expect(submitButton).toBeEnabled({ timeout: 30_000 });
        const responsePromise = this.page.waitForResponse(`api/quiz/exercises/*/submissions/live?submit=true`);
        await submitButton.click();
        return await responsePromise;
    }
}
