import { Response } from '@playwright/test';
import { BASE_API, ExerciseType } from '../../constants';
import { annotateRecovery } from '../../utils';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the file upload exercise assessment page.
 */
export class FileUploadExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    getInstructionsRootElement() {
        return this.page.locator('#instructions-card');
    }

    async downloadSubmissionFile() {
        await this.page.locator('[data-testid="e2e-download-file"]').click();
    }

    /**
     * Submits the assessment and returns the response of the server, which callers assert on.
     * On a multi-node cluster the very first submit occasionally fails with a 5xx while the feedbacks of the result are still being
     * invalidated across the nodes; such a failure is retried once, and the retry is recorded in the report.
     */
    async submitFeedback() {
        let response: Response | undefined;
        for (let attempt = 0; attempt < 2; attempt++) {
            const responsePromise = this.page.waitForResponse(`${BASE_API}/fileupload/file-upload-submissions/*/feedback*`);
            await this.page.locator('#submit').click();
            response = await responsePromise;
            if (response.status() < 400) {
                break;
            }
            annotateRecovery(`submitFeedback: the server answered ${response.status()} to the first submit of the file upload assessment; submitting again`);
        }
        return response!;
    }

    override async rejectComplaint(response: string, examMode: boolean, complaintExerciseTitle?: string) {
        return await super.rejectComplaint(response, examMode, ExerciseType.FILE_UPLOAD, complaintExerciseTitle);
    }

    override async acceptComplaint(response: string, examMode: boolean, complaintExerciseTitle?: string) {
        return await super.acceptComplaint(response, examMode, ExerciseType.FILE_UPLOAD, complaintExerciseTitle);
    }
}
