import { EXERCISE_TYPE } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the file upload exercise assessment page.
 */
export class FileUploadExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    getInstructionsRootElement() {
        return cy.get('#instructions-card');
    }

    submitFeedback() {
        cy.get('#submit').click();
    }

    rejectComplaint(response: string, examMode: boolean) {
        return super.rejectComplaint(response, examMode, EXERCISE_TYPE.FileUpload);
    }

    acceptComplaint(response: string, examMode: boolean) {
        return super.acceptComplaint(response, examMode, EXERCISE_TYPE.FileUpload);
    }
}
