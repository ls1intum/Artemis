import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';

/**
 * A class which encapsulates UI selectors and actions for a text exercise feedback page.
 */
export class ModelingExerciseFeedbackPage extends AbstractExerciseFeedback {
    shouldShowComponentFeedback(component: number, points: number, feedback: string) {
        cy.get('#component-feedback-table').children().eq(1).children().eq(component).should('contain.text', feedback).and('contain.text', `${points}`);
    }
}
