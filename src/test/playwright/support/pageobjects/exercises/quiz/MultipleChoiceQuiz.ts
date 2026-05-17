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
        await scope
            .locator('#answer-option-' + optionNumber)
            .locator('#mc-answer-selection-' + optionNumber)
            .click();
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
