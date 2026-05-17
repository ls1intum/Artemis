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
        // Wait for the option to mount AND for the submit button to also be rendered. Under
        // heavy multi-node load Playwright's click can fire on a freshly-mounted DOM div
        // before Angular wires the (click) handler that toggles the answer, so the click
        // silently no-ops and the submit button stays disabled. Waiting for the submit
        // button (rendered alongside the option in the same component tree) gives Angular
        // a clear signal that the route is interactive.
        await answerOption.waitFor({ state: 'visible', timeout: 30_000 });
        await this.page.locator('#submit-exercise, #submit-exercise-popover, #submit-quiz').first().waitFor({ state: 'attached', timeout: 30_000 });
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
