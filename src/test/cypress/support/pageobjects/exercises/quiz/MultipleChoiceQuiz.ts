import { BASE_API, POST } from '../../../constants';
import { getExercise } from '../../../utils';

export class MultipleChoiceQuiz {
    tickAnswerOption(exerciseID: number, optionNumber: number, quizQuestionId = 0) {
        getExercise(exerciseID)
            .find('#question' + quizQuestionId)
            .find('#answer-option-' + optionNumber)
            .find('#mc-answer-selection-' + optionNumber)
            .click();
    }

    submit() {
        cy.intercept(POST, BASE_API + 'exercises/*/submissions/live').as('createQuizExercise');
        cy.get('#submit-quiz').click();
        return cy.wait('@createQuizExercise');
    }
}
