import { BASE_API, POST } from '../../constants';
/**
 * Parent class for all exercise feedback pages (/course/./exercise/./participate/.)
 */
export abstract class AbstractExerciseFeedback {
    readonly resultSelector = 'jhi-result';

    shouldShowAdditionalFeedback(points: number, feedbackText: string) {
        cy.get('.unreferencedFeedback').contains(`${points} Points: ${feedbackText}`).should('be.visible');
    }

    shouldShowScore(achievedPoints: number, maxPoints: number, percentage: number) {
        cy.get(this.resultSelector).contains(`${percentage}%`);
        cy.get(this.resultSelector).contains(`${achievedPoints} of ${maxPoints} points`);
    }

    complain(complaint: string) {
        cy.get('.btn-primary').contains('Complain').click();
        cy.get('#complainTextArea').type(complaint, { parseSpecialCharSequences: false });
        cy.intercept(POST, BASE_API + 'complaints').as('postComplaint');
        cy.get('.btn-primary').contains('Submit a complaint').click();
        cy.contains('Your complaint has been submitted!').should('be.visible');
        return cy.wait('@postComplaint').its('response.statusCode').should('eq', 201);
    }
}
