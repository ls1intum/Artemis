import { EXERCISE_BASE } from '../../../constants';
import { getExercise } from '../../../utils';
import { Page } from '@playwright/test';

export class MultipleChoiceQuiz {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async tickAnswerOption(exerciseID: number, optionNumber: number, quizQuestionId = 0) {
        await getExercise(this.page, exerciseID)
            .locator('#question' + quizQuestionId)
            .locator('#answer-option-' + optionNumber)
            .locator('#mc-answer-selection-' + optionNumber)
            .click();
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`${EXERCISE_BASE}/*/submissions/live`);
        await this.page.locator('#submit-quiz').click();
        return await responsePromise;
    }
}
