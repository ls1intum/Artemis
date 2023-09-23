import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';
import { OnlineEditorPage } from './OnlineEditorPage';

/**
 * A class which encapsulates UI selectors and actions for a programming exercise feedback page.
 */
export class ProgrammingExerciseFeedbackPage extends AbstractExerciseFeedback {
    shouldShowAdditionalFeedback(points: number, feedbackText: string) {
        cy.reloadUntilFound(this.additionalFeedbackSelector);
        cy.get(this.additionalFeedbackSelector).contains(`${points} Points: ${feedbackText}`).should('be.visible');
    }

    shouldShowCodeFeedback(exerciseID: number, filename: string, feedback: string, points: string, editorPage: OnlineEditorPage) {
        editorPage.openFileWithName(exerciseID, filename);
        this.findVisibleInlineFeedback().contains(feedback).should('be.visible');
        this.findVisibleInlineFeedback().contains(`${points}P`).should('be.visible');
    }

    private findVisibleInlineFeedback() {
        // The additional `jhi-ace-editor` selector is needed because sometimes, the ACE editor will
        // copy the feedback to other temporary locations in the DOM.
        // => The ID is not unique anymore!
        return cy.get('jhi-ace-editor [id*="code-editor-inline-feedback-"]').should('be.visible');
    }

    shouldShowRepositoryLockedWarning() {
        cy.get('#repository-locked-warning').should('be.visible');
    }
}
