import { BASE_API, ExerciseType, POST } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the text exercise assessment page.
 */
export class TextExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    getInstructionsRootElement() {
        return cy.get('#instructions-card');
    }

    provideFeedbackOnTextSection(sectionIndex: number, points: number, feedback: string) {
        this.getFeedbackSection(sectionIndex).click();
        this.typeIntoFeedbackEditor(sectionIndex, feedback);
        this.typePointsIntoFeedbackEditor(sectionIndex, points);
    }

    private typeIntoFeedbackEditor(sectionIndex: number, feedbackText: string) {
        this.getFeedbackSection(sectionIndex).find('#feedback-editor-text-input').type(feedbackText, { parseSpecialCharSequences: false });
    }

    private typePointsIntoFeedbackEditor(sectionIndex: number, feedbackPoints: number) {
        this.getFeedbackSection(sectionIndex).find('#feedback-editor-points-input').clear().type(feedbackPoints.toString());
    }

    private getFeedbackSection(sectionIndex: number) {
        return cy.get('#text-feedback-block-' + sectionIndex);
    }

    submit() {
        // Feedback route is special for text exercises, so we override parent here...
        cy.intercept(POST, `${BASE_API}/participations/*/results/*/submit-text-assessment`).as('submitFeedback');
        cy.get('#submit').click();
        return cy.wait('@submitFeedback');
    }

    rejectComplaint(response: string, examMode: boolean) {
        return super.rejectComplaint(response, examMode, ExerciseType.TEXT);
    }

    acceptComplaint(response: string, examMode: boolean) {
        return super.acceptComplaint(response, examMode, ExerciseType.TEXT);
    }

    getWordCountElement() {
        return cy.get('#text-assessment-word-count');
    }

    getCharacterCountElement() {
        return cy.get('#text-assessment-character-count');
    }
}
