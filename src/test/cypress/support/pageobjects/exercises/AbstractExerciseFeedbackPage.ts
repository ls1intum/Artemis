import { Interception } from 'cypress/types/net-stubbing';

import { BASE_API, POST } from '../../constants';

/**
 * Parent class for all exercise feedback pages (/course/./exercise/./participate/.)
 */
export abstract class AbstractExerciseFeedback {
    readonly resultSelector = '#result';
    readonly additionalFeedbackSelector = '#additional-feedback';
    readonly complainButtonSelector = '#complain';

    shouldShowAdditionalFeedback(points: number, feedbackText: string) {
        if (Math.abs(points) === 1) {
            cy.get(this.additionalFeedbackSelector).contains(`${points} Point: ${feedbackText}`).should('be.visible');
        } else {
            cy.get(this.additionalFeedbackSelector).contains(`${points} Points: ${feedbackText}`).should('be.visible');
        }
    }

    shouldShowScore(percentage: number) {
        cy.get(this.resultSelector).contains(`${percentage}%`);
    }

    complain(complaint: string) {
        cy.reloadUntilFound(this.complainButtonSelector);
        cy.get(this.complainButtonSelector).click();
        cy.get('#complainTextArea').type(complaint, { parseSpecialCharSequences: false });
        cy.intercept(POST, `${BASE_API}/complaints`).as('postComplaint');
        cy.get('#submit-complaint').click();
        return cy.wait('@postComplaint').then((request: Interception) => {
            expect(request.response!.statusCode).to.eq(201);
        });
    }
}
