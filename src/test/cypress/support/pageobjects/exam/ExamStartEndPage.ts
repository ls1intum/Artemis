import { artemis } from '../../ArtemisTesting';
import { POST } from '../../constants';

export class ExamStartEndPage {
    enterFirstnameLastname() {
        artemis.users.getAccountInfo((account: any) => cy.get('#fullname').type((account.firstName ?? '') + ' ' + (account.lastName ?? '')));
    }

    setConfirmCheckmark() {
        cy.get('#confirmBox').click();
    }

    startExam() {
        cy.get('[jhitranslate="artemisApp.exam.startExam"]').click();
    }

    finishExam() {
        cy.intercept(POST, '/api/courses/*/exams/*/student-exams/submit').as('finishExam');
        cy.get('.btn').contains('Finish').click();
        return cy.wait('@finishExam');
    }
}
