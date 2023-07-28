import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';

/**
 * A class which encapsulates UI selectors and actions for a file upload exercise feedback page.
 */
export class FileUploadExerciseFeedbackPage extends AbstractExerciseFeedback {
    shouldShowTextFeedback(feedbackIndex: number, feedback: string) {
        cy.get('#text-feedback-' + feedbackIndex)
            .contains(feedback)
            .should('be.visible');
    }
}
