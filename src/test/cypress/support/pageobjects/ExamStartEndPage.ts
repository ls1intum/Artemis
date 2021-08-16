import { artemis } from '../ArtemisTesting';

export class ExamStartEndPage {
    enterFirstnameLastname() {
        artemis.users.getAccountInfo((account: any) => cy.get('#fullname').type((account.firstName ?? '') + ' ' + (account.lastName ?? '')));
    }

    setConfirmCheckmark(timeout?: number) {
        cy.get('#confirmBox', { timeout }).click();
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

    finishExam(timeout = 20000) {
        this.setConfirmCheckmark(timeout);
        this.enterFirstnameLastname();
        this.pressFinish();
    }
}
