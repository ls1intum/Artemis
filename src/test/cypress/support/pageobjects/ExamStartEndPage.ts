import { artemis } from '../ArtemisTesting';

export class ExamStartEndPage {
    enterFirstnameLastname() {
        artemis.users.getAccountInfo((account: any) => cy.get('#fullname').type((account.firstName ?? '') + ' ' + (account.lastName ?? '')));
    }

    setConfirmCheckmark() {
        cy.get('#confirmBox').click();
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

    finishExam() {
        this.setConfirmCheckmark();
        this.enterFirstnameLastname();
        this.pressFinish();
    }
}
