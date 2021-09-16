import { BASE_API, POST } from '../constants';

export class MultipleChoiceQuiz {
    getQuizBody() {
        return cy.get('.mc-question');
    }

    tickAnswerOption(optionNumber: number) {
        this.getQuizBody()
            .get('#answer-option-' + optionNumber).find('.svg-inline--fa').eq(0)
            .click();
    }

    submit() {
        cy.intercept(POST, BASE_API + 'exercises/*/submissions/live').as('createQuizExercise');
        cy.get('.jhi-btn').should('have.text', 'Submit').click();
        return cy.wait('@createQuizExercise');
    }
}
