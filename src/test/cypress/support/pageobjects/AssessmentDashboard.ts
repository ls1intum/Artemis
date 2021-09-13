export class AssessmentDashboard {
    openExerciseDashboard() {
        cy.get('[jhitranslate="entity.action.exerciseDashboard"]').click();
    }

    confirmInstruction() {
        cy.contains(/I have read and understood the instructions|Start participating in the exercise/).click();
    }

    startNewAssessment() {
        cy.contains('Start new assessment').should('be.visible').click();
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

    submitAssessment() {
        cy.get('[jhitranslate="entity.action.submit"]').click();
        cy.on('window:confirm', () => true);
    }
}
