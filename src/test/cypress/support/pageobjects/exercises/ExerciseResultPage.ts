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
        cy.contains(`${percentage}%`).should('be.visible');
    }

    clickViewSubmission() {
        cy.intercept(GET, BASE_API + 'results/*/rating').as('getResults');
        cy.get('#view-submission').click();
        return cy.wait('@getResults');
    }

    clickOpenCodeEditor(exerciseId: number) {
        cy.get('#open-exercise-' + exerciseId).click();
    }
}
