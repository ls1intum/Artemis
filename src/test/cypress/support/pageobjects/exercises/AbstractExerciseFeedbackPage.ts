import { Interception } from 'cypress/types/net-stubbing';

import { BASE_API, POST } from '../../constants';

/**
 * Parent class for all exercise feedback pages (/course/./exercise/./participate/.)
 */
export abstract class AbstractExerciseFeedback {
    readonly RESULT_SELECTOR = '#result';
    readonly ADDITIONAL_FEEDBACK_SELECTOR = '#additional-feedback';
    readonly COMPLAIN_BUTTON_SELECTOR = '#complain';

    shouldShowAdditionalFeedback(points: number, feedbackText: string) {
        if (Math.abs(points) === 1) {
            cy.get(this.ADDITIONAL_FEEDBACK_SELECTOR).contains(`${points} Point: ${feedbackText}`).should('be.visible');
        } else {
            cy.get(this.ADDITIONAL_FEEDBACK_SELECTOR).contains(`${points} Points: ${feedbackText}`).should('be.visible');
        }
    }

    shouldShowScore(percentage: number) {
        cy.get(this.RESULT_SELECTOR).contains(`${percentage}%`);
    }

    complain(complaint: string) {
        cy.reloadUntilFound(this.COMPLAIN_BUTTON_SELECTOR);
        cy.get(this.COMPLAIN_BUTTON_SELECTOR).click();
        cy.get('#complainTextArea').type(complaint, { parseSpecialCharSequences: false });
        cy.intercept(POST, `${BASE_API}/complaints`).as('postComplaint');
        cy.get('#submit-complaint').click();
        return cy.wait('@postComplaint').then((request: Interception) => {
            expect(request.response!.statusCode).to.eq(201);
        });
    }
}
