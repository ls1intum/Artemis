import { expect } from '@playwright/test';
import { BASE_API, ExerciseType } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the text exercise assessment page.
 */
export class TextExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    getInstructionsRootElement() {
        return this.page.locator('#instructions-card');
    }

    async provideFeedbackOnTextSection(sectionIndex: number, points: number, feedback: string) {
        await this.getFeedbackSection(sectionIndex).click();
        await this.typeIntoFeedbackEditor(sectionIndex, feedback);
        await this.typePointsIntoFeedbackEditor(sectionIndex, points);
    }

    private async typeIntoFeedbackEditor(sectionIndex: number, feedbackText: string) {
        await this.getFeedbackSection(sectionIndex).locator('.unified-feedback-detail-input').fill(feedbackText);
    }

    private async typePointsIntoFeedbackEditor(sectionIndex: number, feedbackPoints: number) {
        await this.setPointsViaStepper(this.getFeedbackSection(sectionIndex), feedbackPoints);
    }

    private getFeedbackSection(sectionIndex: number) {
        return this.page.locator(`#text-feedback-block-${sectionIndex}`);
    }

    /**
     * Cancels the open assessment, accepting the confirmation dialog, and returns the cancel response.
     * The result id is part of the request: the server releases the correction round the editor has open rather than
     * resolving one itself, which used to release the newest round instead (issue #13396).
     */
    async cancelAssessment() {
        const cancelButton = this.page.locator('#cancel');
        await cancelButton.waitFor({ state: 'visible' });
        await expect(cancelButton).toBeEnabled({ timeout: 10000 });
        this.page.once('dialog', (dialog) => dialog.accept());
        const responsePromise = this.page.waitForResponse(
            (response) => /\/submissions\/\d+\/cancel-assessment(\?|$)/.test(response.url().replace(/^[^?]*?(\/api)/, '$1')) && response.request().method() === 'POST',
        );
        await cancelButton.click();
        return await responsePromise;
    }

    override async submit() {
        // Retry on multi-node 5xx flakes (Hazelcast Result.feedbacks ordered-list invalidation lag)
        // so the test surfaces the genuine outcome instead of a transient cluster cache error.
        for (let attempt = 0; attempt < 3; attempt++) {
            const responsePromise = this.page.waitForResponse(`${BASE_API}/text/participations/*/results/*/submit-text-assessment`);
            await this.page.locator('#submit').click();
            const response = await responsePromise;
            if (response.status() < 400 || attempt === 2) {
                return response;
            }
            await this.page.waitForTimeout(1500);
        }
        throw new Error('TextExerciseAssessment.submit exhausted retries');
    }

    override async rejectComplaint(response: string, examMode: boolean) {
        return await super.rejectComplaint(response, examMode, ExerciseType.TEXT);
    }

    override async acceptComplaint(response: string, examMode: boolean) {
        return await super.acceptComplaint(response, examMode, ExerciseType.TEXT);
    }

    getWordCountElement() {
        return this.page.locator('#text-assessment-word-count');
    }

    getCharacterCountElement() {
        return this.page.locator('#text-assessment-character-count');
    }
}
