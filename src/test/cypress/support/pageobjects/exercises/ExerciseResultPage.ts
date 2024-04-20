import { BASE_API, GET } from '../../constants';

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
        cy.reloadUntilFound('#submission-result-graded');
        cy.contains('.tab-bar-exercise-details', `${percentage}%`).should('be.visible');
    }

    clickOpenExercise(exerciseId: number) {
        cy.intercept(GET, `${BASE_API}/results/*/rating`).as('getResults');
        cy.get('#open-exercise-' + exerciseId).click();
        return cy.wait('@getResults');
    }

    clickOpenCodeEditor(exerciseId: number) {
        cy.get('#open-exercise-' + exerciseId).click();
    }
}
