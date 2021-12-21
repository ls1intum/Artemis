import { artemis } from '../../ArtemisTesting';
import { BASE_API, POST } from '../../constants';

export class ExamStartEndPage {
    enterFirstnameLastname() {
        artemis.users.getAccountInfo((account: any) => cy.get('#fullname').type((account.firstName ?? '') + ' ' + (account.lastName ?? '')));
    }

    setConfirmCheckmark(timeout?: number) {
        cy.get('#confirmBox', { timeout }).check();
    }

    pressStart() {
        cy.get('#start-exam').click();
    }

    pressFinish() {
        cy.intercept(POST, BASE_API + 'courses/*/exams/*/student-exams/submit').as('finishExam');
        cy.get('.btn').contains('Finish').click();
        return cy.wait('@finishExam');
    }

    startExam() {
        this.setConfirmCheckmark();
        this.enterFirstnameLastname();
        this.pressStart();
    }

    finishExam(timeout?: number) {
        this.setConfirmCheckmark(timeout ? timeout : Cypress.config('defaultCommandTimeout'));
        this.enterFirstnameLastname();
        return this.pressFinish();
    }
}
