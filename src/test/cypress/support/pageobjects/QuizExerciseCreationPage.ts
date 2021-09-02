import { POST } from '../constants';

export class QuizExerciseCreationPage {
    setTitle(title: string) {
        cy.get('#quiz-title').type(title);
    }

    addMultipleChoiceQuestion(title: string) {
        cy.get('#quiz-add-mc-question').click();
        cy.get('#mc-question-title').type(title);
        cy.fixture('quiz_exercise_fixtures/MultipleChoiceQuiz.txt').then((fileContent) => {
            cy.get('.ace_text-input').focus().clear().type(fileContent);
        });
    }

    addShortAnswerQuestion(title: string) {
        cy.get('#quiz-add-short-answer-question').click();
        cy.get('#short-answer-question-title').type(title);
        cy.fixture('quiz_exercise_fixtures/ShortAnswerQuiz.txt').then((fileContent) => {
            cy.get('.ace_text-input').focus().clear().type(fileContent);
            cy.get('[jhitranslate="artemisApp.shortAnswerQuestion.editor.visual"]').click();
        });
    }

    saveQuiz() {
        cy.intercept(POST, '/api/quiz-exercises').as('createQuizExercise');
        cy.contains('Save').click();
        return cy.wait('@createQuizExercise');
    }
}
