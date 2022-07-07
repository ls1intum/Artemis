import { COURSE_BASE } from '../../requests/CourseManagementRequests';
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
        cy.get('#start-exam').click();
    }

    pressFinish() {
        cy.intercept(POST, COURSE_BASE + '*/exams/*/student-exams/submit').as('finishExam');
        cy.get('#end-exam').click();
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
