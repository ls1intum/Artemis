export class MultipleChoiceQuiz {

    getQuizBody() {
        return cy.get('.mc-question');
    }

    tickAnswerOption(optionNumber: number) {
        this.getQuizBody().get('#answer-option-' + optionNumber + ' > .selection > .ng-fa-icon > .svg-inline--fa').click();
    }

    submit() {
        cy.get('.jhi-btn').contains('Submit').click();
    }
}
