import { artemis } from '../../ArtemisTesting';
import { POST } from '../../constants';

export class ExamStartEndPage {
    enterFirstnameLastname() {
        artemis.users.getAccountInfo((account: any) => cy.get('#fullname').type((account.firstName ?? '') + ' ' + (account.lastName ?? '')));
    }

    setConfirmCheckmark(timeout?: number) {
        cy.get('#confirmBox', { timeout }).check();
    }

    pressStart() {
        cy.get('[jhitranslate="artemisApp.exam.startExam"]').click();
    }

    pressFinish() {
        cy.intercept(POST, '/api/courses/*/exams/*/student-exams/submit').as('finishExam');
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
