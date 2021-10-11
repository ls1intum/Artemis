import { OnlineEditorPage } from '../../OnlineEditorPage';
import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';

/**
 * A class which encapsulates UI selectors and actions for a programming exercise feedback page.
 */
export class ProgrammingExerciseFeedbackPage extends AbstractExerciseFeedback {
    readonly codeFeedbackSelector = '.inline-feedback';

    shouldShowCodeFeedback(filename: string, feedback: string, points: string, editorPage: OnlineEditorPage) {
        editorPage.openFileWithName(filename);
        cy.get(this.codeFeedbackSelector).find('[jhitranslate="artemisApp.assessment.detail.tutorComment"]').should('be.visible');
        cy.get(this.codeFeedbackSelector).contains(feedback).should('be.visible');
        cy.get(this.codeFeedbackSelector).contains(`${points}P`).should('be.visible');
    }

    shouldShowRepositoryLockedWarning() {
        cy.get('[jhitranslate="artemisApp.programmingExercise.repositoryIsLocked.title"]').should('be.visible');
    }
}
