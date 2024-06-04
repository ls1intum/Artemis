import { Page } from '@playwright/test';
import { BASE_API } from '../../../constants';

export class QuizExerciseParticipationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Joins a quiz batch using the given password.
     * @param batchPassword The password of the batch to join.
     */
    async joinQuizBatch(batchPassword: string) {
        await this.page.locator('#join-patch-password').fill(batchPassword);
        await this.page.locator('#join-batch').click();
    }

    /**
     * Starts the quiz batch.
     */
    async startQuizBatch() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/quiz-exercises/*/join`);
        await this.page.locator('#start-batch').click();
        await responsePromise;
    }

    /**
     * Returns the locator for the waiting for quiz start alert.
     */
    getWaitingForStartAlert() {
        return this.page.locator('.quiz-waiting-for-start-overlay');
    }

    /**
     * Returns the locator for the quiz question.
     * @param questionIndex The index of the question.
     */
    getQuizQuestion(questionIndex: number) {
        return this.page.locator(`#question${questionIndex}`);
    }
}
