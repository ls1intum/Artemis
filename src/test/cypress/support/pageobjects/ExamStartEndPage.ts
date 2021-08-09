import { artemis } from '../ArtemisTesting';

export class ExamStartEndPage {

    enterFirstnameLastname() {
        artemis.users.getAccountInfo((account: any) => cy.get('#fullname').type((account.firstName ?? '') + ' ' + (account.lastName ?? '')));
    }

    setConfirmCheckmark() {
        cy.get('#confirmBox').click();
    }

    startExam() {
        cy.contains('Start').click();
    }

    finishExam() {
        cy.get('.btn').contains('Finish').click();
    }
}
