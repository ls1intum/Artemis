import { COURSE_BASE } from '../../requests/CourseManagementRequests';
import { users } from '../../users';
import { GET, POST } from '../../constants';

export class ExamStartEndPage {
    enterFirstnameLastname() {
        users.getAccountInfo((account: any) => cy.get('#fullname').type((account.firstName ?? '') + ' ' + (account.lastName ?? '')));
    }

    setConfirmCheckmark(timeout?: number) {
        cy.get('#confirmBox', { timeout }).check();
    }

    pressStart() {
        cy.get('#start-exam').click();
    }

    clickContinue() {
        cy.get('#continue').click();
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

    pressShowSummary() {
        cy.intercept(GET, COURSE_BASE + '*/exams/*/student-exams/*/summary').as('examSummaryDownload');
        cy.get('#showExamSummaryButton', { timeout: 20000 }).should('be.visible').click();
        cy.wait('@examSummaryDownload');
    }
}
