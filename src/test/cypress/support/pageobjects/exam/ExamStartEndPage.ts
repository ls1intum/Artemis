import { COURSE_BASE, GET, POST } from '../../constants';
import { users } from '../../users';

export class ExamStartEndPage {
    enterFirstnameLastname() {
        users.getAccountInfo((account: any) => cy.get('#fullname').type((account.firstName ?? '') + ' ' + (account.lastName ?? '')));
    }

    setConfirmCheckmark(timeout?: number) {
        cy.get('#confirmBox', { timeout }).check();
    }

    pressStartWithWait() {
        cy.intercept(GET, `${COURSE_BASE}/*/exams/*/student-exams/*/conduction`).as('startExam');
        cy.get('#start-exam').click();
        return cy.wait('@startExam');
    }

    pressStart() {
        cy.get('#start-exam').click();
    }

    clickContinue() {
        cy.get('#continue').click();
    }

    pressFinish() {
        cy.intercept(POST, `${COURSE_BASE}/*/exams/*/student-exams/submit`).as('finishExam');
        cy.get('#end-exam').click();
        return cy.wait('@finishExam', { timeout: 10000 });
    }

    startExam(withWait = false) {
        this.setConfirmCheckmark();
        this.enterFirstnameLastname();
        if (withWait) {
            this.pressStartWithWait();
        } else {
            this.pressStart();
        }
    }

    finishExam(timeout?: number) {
        this.setConfirmCheckmark(timeout ? timeout : Cypress.config('defaultCommandTimeout'));
        this.enterFirstnameLastname();
        return this.pressFinish();
    }

    pressShowSummary() {
        cy.intercept(GET, `${COURSE_BASE}/*/exams/*/student-exams/*/summary`).as('examSummaryDownload');
        cy.get('#showExamSummaryButton').should('be.visible').should('not.have.attr', 'disabled', { timeout: 15000 }).click();
        cy.wait('@examSummaryDownload');
    }
}
