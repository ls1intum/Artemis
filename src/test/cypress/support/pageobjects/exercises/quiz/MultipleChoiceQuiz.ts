import { BASE_API, POST } from '../../../constants';

export class MultipleChoiceQuiz {
    private getQuizBody(quizQuestionId: number) {
        return cy.get('#question' + quizQuestionId);
    }

    tickAnswerOption(optionNumber: number, quizQuestionId = 0) {
        this.getQuizBody(quizQuestionId)
            .get('#answer-option-' + optionNumber)
            .find('#mc-answer-selection-' + optionNumber)
            .click();
    }

    submit() {
        cy.intercept(POST, BASE_API + 'exercises/*/submissions/live').as('createQuizExercise');
        cy.get('#submit-quiz').click();
        return cy.wait('@createQuizExercise');
    }
}
