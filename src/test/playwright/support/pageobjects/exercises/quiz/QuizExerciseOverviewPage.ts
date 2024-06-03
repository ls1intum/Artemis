import { Page } from '@playwright/test';
import { BASE_API } from '../../../constants';
import { QuizBatch } from 'app/entities/quiz/quiz-exercise.model';

export class QuizExerciseOverviewPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Adds a quiz batch to the quiz exercise with the given ID.
     * @param exerciseId The ID of the quiz exercise to add a batch to.
     * @returns The created quiz batch.
     */
    async addQuizBatch(exerciseId: number): Promise<QuizBatch> {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/quiz-exercises/${exerciseId}/add-batch`);
        await this.page.locator(`#instructor-quiz-add-${exerciseId}`).click();
        const response = await responsePromise;
        return await response.json();
    }

    /**
     * Starts the quiz batch with the given batch ID for the given exercise.
     * @param exerciseId The ID of the quiz exercise the batch belongs to.
     * @param batchId The ID of the batch to start.
     */
    async startQuizBatch(exerciseId: number, batchId: number) {
        await this.page.locator(`#instructor-quiz-start-${exerciseId}-${batchId}`).click();
    }
}
