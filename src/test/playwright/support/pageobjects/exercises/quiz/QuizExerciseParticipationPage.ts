import { Locator, Page, expect } from '@playwright/test';
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

    async startIndividualQuizBatch() {
        await this.page.locator('#start-batch').click();
    }

    /**
     * Starts the quiz batch.
     */
    async startQuizBatch() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/quiz/quiz-exercises/*/join`);
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

    /**
     * Submits the current PRACTICE attempt via the shared exercise-header submit button and waits for the
     * practice submission endpoint (`/submissions/practice`, not the live one). Returns the practice submit response.
     */
    async submitPractice() {
        const submitButton = this.page.locator('#submit-exercise, #submit-exercise-popover, #submit-quiz').first();
        await expect(submitButton).toBeEnabled({ timeout: 30_000 });
        const responsePromise = this.page.waitForResponse(`${BASE_API}/quiz/exercises/*/submissions/practice`);
        await submitButton.click();
        return await responsePromise;
    }

    /**
     * Opens the result-history dropdown in the exercise header. Clicks the dropdown arrow (the
     * jhi-result-history-dropdown element) rather than the status badge: the badge is wrapped in a span that calls
     * stopPropagation, so a click there never reaches the trigger's toggle handler. The arrow sits outside that span
     * and bubbles up to the toggle.
     */
    async openResultHistory() {
        const trigger = this.page.getByTestId('result-history-trigger');
        await trigger.waitFor({ state: 'visible', timeout: 30_000 });
        await this.page.locator('jhi-result-history-dropdown').click();
    }

    /**
     * Returns the locator for the individual attempt rows inside the opened result-history dropdown.
     */
    getResultHistoryRows(): Locator {
        return this.page.getByTestId('result-history-row');
    }

    /**
     * Returns the locator for the multiple-choice per-question result table (Answer / Solution / You columns with
     * correct/wrong markers). It is rendered only when {@code showResult} fired, so its visibility proves the quiz
     * result (with per-question correctness) is displayed for the viewed attempt.
     */
    getMultipleChoiceResultTable(): Locator {
        return this.page.locator('.answer-options-result');
    }
}
