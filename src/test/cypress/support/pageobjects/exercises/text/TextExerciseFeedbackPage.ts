import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';

/**
 * A class which encapsulates UI selectors and actions for a text exercise feedback page.
 */
export class TextExerciseFeedbackPage extends AbstractExerciseFeedback {
    shouldShowTextFeedback(feedbackIndex: number, feedback: string) {
        cy.get('#text-feedback-' + feedbackIndex)
            .contains(feedback)
            .should('be.visible');
    }
}
