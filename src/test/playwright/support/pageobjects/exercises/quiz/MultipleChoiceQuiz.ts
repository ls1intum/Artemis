import { getExercise } from '../../../utils';
import { Page } from '@playwright/test';

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

    async submit() {
        const responsePromise = this.page.waitForResponse(`api/quiz/exercises/*/submissions/live?submit=true`);
        await this.page.locator('#submit-exercise, #submit-exercise-popover, #submit-quiz').first().click();
        return await responsePromise;
    }
}
