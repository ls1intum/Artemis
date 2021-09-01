import { EXERCISE_BASE, POST } from '../constants';

export class MultipleChoiceQuiz {
    getQuizBody() {
        return cy.get('.mc-question');
    }

    tickAnswerOption(optionNumber: number) {
        this.getQuizBody()
            .get('#answer-option-' + optionNumber + ' > .selection > .ng-fa-icon > .svg-inline--fa')
            .click();
    }

    submit() {
        cy.intercept(POST, EXERCISE_BASE + '*/submissions/live').as('createSubmission');
        cy.get('.jhi-btn').contains('Submit').click();
        return cy.wait('@createSubmission');
    }
}
