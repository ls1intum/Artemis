import { POST, BASE_API } from '../../../constants';

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
            cy.get('#short-answer-show-visual').click();
        });
    }

    addDragAndDropQuestion(title: string) {
        cy.get('#quiz-add-dnd-question').click();
        cy.get('#drag-and-drop-question-title').type(title);
        this.uploadDragAndDropBackground().then(() => {
            cy.wait(2000);
            cy.get('.click-layer').trigger('mousedown', { x: 50, y: 50 }).trigger('mousemove', { x: 500, y: 300 }).trigger('mouseup');
        });
        this.createDragAndDropItem('Rick Astley');
        cy.get('#drag-item-0').drag('#drop-location');
        cy.fixture('quiz_exercise_fixtures/dragAndDropQuiz.txt').then((fileContent) => {
            cy.get('.ace_text-input').focus().clear().type(fileContent);
        });
    }

    createDragAndDropItem(text: string) {
        cy.get('#add-text-drag-item').click();
        cy.get('#drag-item-0-text').clear().type(text);
    }

    uploadDragAndDropBackground() {
        cy.get('#background-image-input-form').children().eq(0).attachFile('quiz_exercise_fixtures/dragAndDrop_background.jpg');
        cy.intercept(POST, BASE_API + 'fileUpload*').as('uploadBackground');
        cy.get('#background-image-input-form').children().eq(1).click();
        return cy.wait('@uploadBackground');
    }

    /**
     * @return <Chainable>  the response of the request
     */
    saveQuiz() {
        cy.intercept(POST, BASE_API + 'quiz-exercises').as('createQuizExercise');
        cy.get('#quiz-save').click();
        return cy.wait('@createQuizExercise');
    }
}
