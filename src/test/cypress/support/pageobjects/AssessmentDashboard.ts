import { BASE_API, POST, PUT } from '../constants';

export class AssessmentDashboard {
    openExerciseDashboard() {
        cy.get('[jhitranslate="entity.action.exerciseDashboard"]').click();
    }

    confirmInstruction() {
        cy.get('.guided-tour-instructions-button').click();
    }

    startNewAssessment() {
        cy.get('.guided-tour-new-assessment-btn').click();
    }

    startAssessing() {
        this.openExerciseDashboard();
        this.confirmInstruction();
        this.startNewAssessment();
        cy.contains('You have the lock for this assessment').should('be.visible');
    }

    addNewFeedback(points: number, feedback: string) {
        cy.get('.btn').contains('Add new Feedback').click();
        cy.get('.col-lg-6 >>>> :nth-child(1) > :nth-child(2)').clear().type(points.toString());
        cy.get('.col-lg-6 >>>> :nth-child(2) > :nth-child(2)').type(feedback);
    }

    saveAssessment() {
        cy.get('[jhitranslate="entity.action.save"]').click();
    }

    submitModelingAssessment() {
        cy.intercept(PUT, BASE_API + 'modeling-submissions/*/result/*/assessment*').as('submitAssessment');
        this.submitAssessment();
        return cy.wait('@submitAssessment');
    }

    submitTextAssessment() {
        cy.intercept(POST, BASE_API + 'participations/*/results/*/submit-text-assessment').as('submitFeedback');
        this.submitAssessment();
        return cy.wait('@submitFeedback');
    }

    submitAssessment() {
        cy.get('[jhitranslate="entity.action.submit"]').click();
    }
}
