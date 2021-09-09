import { artemis } from '../ArtemisTesting';

export class ExamStartEndPage {
    enterFirstnameLastname() {
        artemis.users.getAccountInfo((account: any) => cy.get('#fullname').type((account.firstName ?? '') + ' ' + (account.lastName ?? '')));
    }

    setConfirmCheckmark(timeout?: number) {
        cy.get('#confirmBox', { timeout }).check();
    }

    pressStart() {
        cy.contains('Start').click();
    }

    pressFinish() {
        cy.get('.btn').contains('Finish').click();
    }

    startExam() {
        this.setConfirmCheckmark();
        this.enterFirstnameLastname();
        this.pressStart();
    }

    finishExam(timeout?: number) {
        this.setConfirmCheckmark(timeout ? timeout : Cypress.config('defaultCommandTimeout'));
        this.enterFirstnameLastname();
        this.pressFinish();
    }
}
