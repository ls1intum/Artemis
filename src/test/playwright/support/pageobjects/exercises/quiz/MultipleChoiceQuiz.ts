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
        // Wait for the option to mount before clicking. Under heavy multi-node load the
        // quiz route component renders after `login`'s navigation completes, so an
        // unguarded click can race the render and silently no-op against a stale DOM.
        await answerOption.waitFor({ state: 'visible', timeout: 30_000 });
        await answerOption.locator('#mc-answer-selection-' + optionNumber).click();
        // Verify the tick took. The component's (click) handler lives on the outer
        // #answer-option div, which sets the `.selected` class when toggled on. If the
        // class hasn't applied within 5s the click landed on the disabled branch
        // (clickDisabled() = true while a submit is in flight or the quiz already
        // ended) — re-click the parent once to force the toggle through.
        const selectedWithin = async (timeout: number): Promise<boolean> =>
            answerOption
                .filter({ has: this.page.locator('.fa-check-square, .fa-dot-circle') })
                .waitFor({ state: 'visible', timeout })
                .then(() => true)
                .catch(() => false);
        if (!(await selectedWithin(5_000))) {
            await answerOption.click();
            await selectedWithin(5_000);
        }
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
