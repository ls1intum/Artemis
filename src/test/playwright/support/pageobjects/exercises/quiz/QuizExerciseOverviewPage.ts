import { Page } from '@playwright/test';
import { BASE_API } from '../../../constants';
import { QuizBatch } from 'app/quiz/shared/entities/quiz-exercise.model';

export class QuizExerciseOverviewPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Opens the batch management popover for the given quiz. The add-batch and per-batch start buttons live inside a
     * PrimeNG popover that is only rendered while open, so callers must open it before interacting with those buttons.
     * Idempotent: does nothing if the popover is already open.
     * @param exerciseId The ID of the quiz exercise whose batch popover to open.
     */
    private async openBatchMenu(exerciseId: number) {
        const addBatchButton = this.page.locator(`#instructor-quiz-add-${exerciseId}`);
        if (await addBatchButton.isVisible()) {
            return;
        }
        await this.page.locator(`#instructor-quiz-batches-${exerciseId}`).click();
        await addBatchButton.waitFor({ state: 'visible' });
    }

    /**
     * Adds a quiz batch to the quiz exercise with the given ID.
     * @param exerciseId The ID of the quiz exercise to add a batch to.
     * @returns The created quiz batch.
     */
    async addQuizBatch(exerciseId: number): Promise<QuizBatch> {
        await this.openBatchMenu(exerciseId);
        const responsePromise = this.page.waitForResponse(`${BASE_API}/quiz/quiz-exercises/${exerciseId}/add-batch`);
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
        await this.openBatchMenu(exerciseId);
        // Wait for the start-batch request to actually complete before returning. Otherwise a student joining the batch
        // immediately afterwards can race the in-flight start, find the batch not yet started, and never see the quiz
        // question — the root cause of the batch-join flake.
        const responsePromise = this.page.waitForResponse(
            (resp) => resp.url().includes(`/quiz/quiz-batches/${batchId}/start-batch`) && resp.request().method() === 'PUT' && resp.ok(),
            { timeout: 20000 },
        );
        await this.page.locator(`#instructor-quiz-start-${exerciseId}-${batchId}`).click();
        await responsePromise;
    }

    /**
     * Exports a quiz exercise.
     *
     * @Note Assumes to be on the details page of a quiz.
     */
    async exportQuizExercise() {
        await this.page.locator('button', { hasText: 'Export' }).click();
    }
}
