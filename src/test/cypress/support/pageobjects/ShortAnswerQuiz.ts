import { POST, EXERCISE_BASE } from '../constants';

export class ShortAnswerQuiz {
    getQuizBody() {
        return cy.get('.sa-question');
    }

    typeAnswer(optionNumber: number, answer: string) {
        this.getQuizBody().get('.short-answer-question-container__input').eq(optionNumber).type(answer);
    }

    submit() {
        cy.intercept(POST, EXERCISE_BASE + '*/submissions/live').as('createQuizExercise');
        cy.get('.jhi-btn').contains('Submit').click();
        return cy.wait('@createQuizExercise');
    }
}
