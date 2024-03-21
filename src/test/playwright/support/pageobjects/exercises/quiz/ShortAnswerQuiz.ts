import { EXERCISE_BASE } from '../../../constants';
import { Page } from '@playwright/test';

export class ShortAnswerQuiz {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getQuizBody() {
        return this.page.locator('#question0');
    }

    async typeAnswer(line: number, column: number, quizQuestionId: number, answer: string) {
        await this.getQuizBody().locator(`#solution-${line}-${column}-${quizQuestionId}`).fill(answer);
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`${EXERCISE_BASE}/*/submissions/live`);
        await this.page.locator('#submit-quiz').click();
        await responsePromise;
    }
}
