import { BASE_API, POST } from '../../../constants';

export class MultipleChoiceQuiz {
    getQuizBody() {
        return cy.get('.mc-question');
    }

    tickAnswerOption(optionNumber: number) {
        this.getQuizBody()
            .get('#answer-option-' + optionNumber)
            .find('.svg-inline--fa')
            .first()
            .click();
    }

    submit() {
        cy.intercept(POST, BASE_API + 'exercises/*/submissions/live').as('createQuizExercise');
        cy.get('.jhi-btn').should('contain.text', 'Submit').click();
        const request = cy.wait('@createQuizExercise');
        cy.get('[jhitranslate="artemisApp.quizExercise.successfullySubmittedText"]').should('be.visible');
        return request;
    }
}
