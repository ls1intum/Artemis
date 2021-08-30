/**
 * A class which encapsulates UI selectors and actions for the static code analysis feedback modal in the online editor.
 */
export class ScaFeedbackModal {
    private readonly feedbackSelector = '.feedback-item, .alert-success';

    shouldShowPointChart() {
        cy.get('jhi-chart').find('canvas').should('be.visible');
    }

    shouldShowFeedback(numberOfPassedTests: number, points: string) {
        cy.get(this.feedbackSelector).contains(`${numberOfPassedTests} passed tests`).should('be.visible');
        cy.get(this.feedbackSelector).contains(`${points}P`).should('be.visible');
    }

    shouldShowCodeIssue(feedbackText: string, pointReduction: string) {
        cy.get('.feedback-text')
            .contains(feedbackText)
            .scrollIntoView()
            .should('be.visible')
            .parents('.feedback-item')
            .find('.feedback-points')
            .scrollIntoView()
            .should('contain.text', `-${pointReduction}P`)
            .and('be.visible');
    }

    closeModal() {
        cy.get('.modal-footer').find('.btn').click();
        cy.get('.modal').should('not.exist');
    }
}
