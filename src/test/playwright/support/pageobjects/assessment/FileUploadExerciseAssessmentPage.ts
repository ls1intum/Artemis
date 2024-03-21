import { ExerciseType } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the file upload exercise assessment page.
 */
export class FileUploadExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    getInstructionsRootElement() {
        return this.page.locator('#instructions-card');
    }

    async submitFeedback() {
        await this.page.locator('#submit').click();
    }

    async rejectComplaint(response: string, examMode: boolean) {
        return await super.rejectComplaint(response, examMode, ExerciseType.FILE_UPLOAD);
    }

    async acceptComplaint(response: string, examMode: boolean) {
        return await super.acceptComplaint(response, examMode, ExerciseType.FILE_UPLOAD);
    }
}
