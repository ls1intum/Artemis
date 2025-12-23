import { BASE_API, ExerciseType } from '../../constants';
import { Page, expect } from '@playwright/test';

/**
 * Parent class for all exercise assessment pages.
 */
export abstract class AbstractExerciseAssessmentPage {
    protected readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async addNewFeedback(points: number, feedback?: string) {
        await this.page.locator('.add-unreferenced-feedback').click();
        await this.fillFeedback(points, feedback);
    }

    async fillFeedback(points: number, feedback?: string) {
        const unreferencedFeedback = this.page.locator('.unreferenced-feedback-detail');
        await unreferencedFeedback.locator('#feedback-points').clear();
        await unreferencedFeedback.locator('#feedback-points').fill(points.toString());
        if (feedback) {
            await unreferencedFeedback.locator('#feedback-textarea').clear();
            await unreferencedFeedback.locator('#feedback-textarea').fill(feedback);
        }
    }

    async submitWithoutInterception() {
        const submitButton = this.page.locator('#submit');
        // Wait for the submit button to be visible and enabled
        await submitButton.waitFor({ state: 'visible' });
        await expect(submitButton).toBeEnabled({ timeout: 10000 });
        // Handle any confirmation dialogs that might appear (e.g., incomplete assessment warning)
        this.page.once('dialog', (dialog) => dialog.accept());
        // Use keyboard shortcut (Ctrl+Enter) which is more reliable than clicking
        await this.page.keyboard.press('Control+Enter');
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/programming/participations/*/manual-results?submit=true`);
        await this.submitWithoutInterception();
        return await responsePromise;
    }

    async rejectComplaint(response: string, examMode: boolean, exerciseType: ExerciseType) {
        return await this.handleComplaint(response, false, exerciseType, examMode);
    }

    async acceptComplaint(response: string, examMode: boolean, exerciseType: ExerciseType) {
        return await this.handleComplaint(response, true, exerciseType, examMode);
    }

    async checkComplaintMessage(message: string) {
        await this.page.locator('.message', { hasText: message }).waitFor({ state: 'attached' });
    }

    async nextAssessment() {
        await this.page.locator('#assessNextButton').click();
        await this.page.locator('#assessNextButton').waitFor({ state: 'hidden' });
    }

    private async handleComplaint(response: string, accept: boolean, exerciseType: ExerciseType, examMode: boolean) {
        if (exerciseType !== ExerciseType.MODELING && !examMode) {
            await this.page.locator('#show-complaint').click();
        }
        await this.page.locator('#responseTextArea').fill(response);

        let responsePromise;
        switch (exerciseType) {
            case ExerciseType.PROGRAMMING:
                responsePromise = this.page.waitForResponse(`${BASE_API}/programming/programming-submissions/*/assessment-after-complaint`);
                break;
            case ExerciseType.TEXT:
                if (accept) {
                    responsePromise = this.page.waitForResponse(`${BASE_API}/text/participations/*/submissions/*/text-assessment-after-complaint`);
                } else {
                    responsePromise = this.page.waitForResponse(`${BASE_API}/assessment/complaints/*/response`);
                }
                break;
            case ExerciseType.MODELING:
                responsePromise = this.page.waitForResponse(`${BASE_API}/assessment/complaints/*/response`);
                break;
            case ExerciseType.FILE_UPLOAD:
                responsePromise = this.page.waitForResponse(`${BASE_API}/fileupload/file-upload-submissions/*/assessment-after-complaint`);
                break;
            default:
                throw new Error(`Exercise type '${exerciseType}' is not supported yet!`);
        }
        if (accept) {
            await this.page.locator('#acceptComplaintButton').click();
        } else {
            await this.page.locator('#rejectComplaintButton').click();
        }
        return await responsePromise;
    }
}
