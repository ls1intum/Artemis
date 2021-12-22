/**
 * A class which encapsulates UI selectors and actions for the exercise result page.
 */
export class ExerciseResultPage {
    shouldShowProblemStatement(problemStatement: string) {
        cy.get('#problem-statement').contains(problemStatement).should('be.visible');
    }

    shouldShowExerciseTitle(title: string) {
        cy.get('#exercise-header').contains(title).should('be.visible');
    }

    shouldShowScore(percentage: number) {
        cy.contains(`Score ${percentage}`).should('be.visible');
    }

    clickViewSubmission() {
        cy.get('#view-submission').click();
    }

    clickOpenCodeEditor(exerciseId: string) {
        cy.get('#open-exercise-' + exerciseId).click();
    }
}
