import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';

/**
 * A class which encapsulates UI selectors and actions for a text exercise feedback page.
 */
export class TextExerciseFeedbackPage extends AbstractExerciseFeedback {
    shouldShowTextFeedback(feedback: string) {
        cy.contains('Feedback: ' + feedback).should('be.visible');
    }
}
