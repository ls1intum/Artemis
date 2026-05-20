import { BASE_API, ExerciseType } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the file upload exercise assessment page.
 */
export class FileUploadExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    getInstructionsRootElement() {
        return this.page.locator('#instructions-card');
    }

    async downloadSubmissionFile() {
        await this.page.locator('#e2e-download-file').click();
    }

    async submitFeedback() {
        // Wait for the assessment PUT to complete and verify the server accepted it.
        // Without this the next test (student view) races the cache and may see "No graded result".
        // Retry once on multi-node 5xx flakes (Hazelcast Result.feedbacks invalidation lag).
        for (let attempt = 0; attempt < 2; attempt++) {
            const responsePromise = this.page.waitForResponse(`${BASE_API}/fileupload/file-upload-submissions/*/feedback*`);
            await this.page.locator('#submit').click();
            const response = await responsePromise;
            if (response.status() < 400 || attempt === 1) {
                return;
            }
            await this.page.waitForTimeout(1500);
        }
    }

    async rejectComplaint(response: string, examMode: boolean) {
        return await super.rejectComplaint(response, examMode, ExerciseType.FILE_UPLOAD);
    }

    async acceptComplaint(response: string, examMode: boolean) {
        return await super.acceptComplaint(response, examMode, ExerciseType.FILE_UPLOAD);
    }
}
