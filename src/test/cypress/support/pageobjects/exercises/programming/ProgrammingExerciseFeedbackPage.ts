import { OnlineEditorPage } from './OnlineEditorPage';
import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';

/**
 * A class which encapsulates UI selectors and actions for a programming exercise feedback page.
 */
export class ProgrammingExerciseFeedbackPage extends AbstractExerciseFeedback {
    shouldShowAdditionalFeedback(points: number, feedbackText: string) {
        cy.reloadUntilFound(this.additionalFeedbackSelector);
        cy.get(this.additionalFeedbackSelector).contains(`${points} Points: ${feedbackText}`).should('be.visible');
    }

    shouldShowCodeFeedback(filename: string, feedback: string, points: string, editorPage: OnlineEditorPage) {
        editorPage.openFileWithName(filename);
        this.findVisibleInlineFeedback().contains(feedback).should('be.visible');
        this.findVisibleInlineFeedback().contains(`${points}P`).should('be.visible');
    }

    private findVisibleInlineFeedback() {
        return cy.get('[id="inline-feedback"]').should('be.visible');
    }

    shouldShowRepositoryLockedWarning() {
        cy.get('#repository-locked-warning').should('be.visible');
    }
}
