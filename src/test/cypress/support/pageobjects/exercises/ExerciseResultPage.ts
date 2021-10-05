/**
 * A class which encapsulates UI selectors and actions for the exercise result page.
 */
export class ExerciseResultPage {
    shouldShowProblemStatement(problemStatement: string) {
        cy.get('.problem-statement').contains(problemStatement).should('be.visible');
    }

    shouldShowExerciseTitle(title: string) {
        cy.contains(title).should('be.visible');
    }

    shouldShowScore(percentage: number) {
        cy.contains(`Score ${percentage}`).should('be.visible');
    }

    clickViewSubmission() {
        cy.contains('View submission').click();
    }
}
