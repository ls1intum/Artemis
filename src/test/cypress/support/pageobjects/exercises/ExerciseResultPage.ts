import { GET, BASE_API } from './../../constants';
/**
 * A class which encapsulates UI selectors and actions for the exercise result page.
 */
export class ExerciseResultPage {
    shouldShowProblemStatement(problemStatement: string) {
        cy.get('.problem-statement').contains(problemStatement).should('be.visible');
    }

    shouldShowExerciseTitle(title: string) {
        cy.get('jhi-header-exercise-page-with-details').contains(title).should('be.visible');
    }

    shouldShowScore(percentage: number) {
        cy.contains(`Score ${percentage}`).should('be.visible');
    }

    clickViewSubmission() {
        cy.intercept(GET, BASE_API + 'results/*/rating').as('getResults');
        cy.contains('View submission').click();
        return cy.wait('@getResults');
    }

    clickOpenCodeEditor() {
        cy.contains('Open code editor').click();
    }
}
