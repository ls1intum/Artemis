import { Dayjs } from 'dayjs/esm';

import { BASE_API, POST, QUIZ_EXERCISE_BASE } from '../../../constants';
import { enterDate } from '../../../utils';

export class QuizExerciseCreationPage {
    setTitle(title: string) {
        cy.get('#field_title').type(title);
    }

    setVisibleFrom(date: Dayjs) {
        enterDate('#pick-releaseDate', date);
    }

    addMultipleChoiceQuestion(title: string, points = 1) {
        cy.get('#quiz-add-mc-question').click();
        cy.get('#mc-question-title').type(title);
        cy.get('#score').clear().type(points.toString());
        cy.fixture('exercise/quiz/multiple_choice/question.txt').then((fileContent) => {
            cy.get('.ace_text-input').focus().clear().type(fileContent);
        });
    }

    addShortAnswerQuestion(title: string) {
        cy.get('#quiz-add-short-answer-question').click();
        cy.get('#short-answer-question-title').type(title);
        cy.fixture('exercise/quiz/short_answer/question.txt').then((fileContent) => {
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
        cy.fixture('exercise/quiz/drag_and_drop/question.txt').then((fileContent) => {
            cy.get('.ace_text-input').focus().clear().type(fileContent);
        });
    }

    createDragAndDropItem(text: string) {
        cy.get('#add-text-drag-item').click();
        cy.get('#drag-item-0-text').clear().type(text);
    }

    uploadDragAndDropBackground() {
        cy.get('#background-image-input-form').children().eq(0).attachFile('exercise/quiz/drag_and_drop/background.jpg');
        cy.intercept(POST, `${BASE_API}/fileUpload*`).as('uploadBackground');
        cy.get('#background-image-input-form').children().eq(1).click();
        return cy.wait('@uploadBackground');
    }

    /**
     * @return <Chainable>  the response of the request
     */
    saveQuiz() {
        cy.intercept(POST, QUIZ_EXERCISE_BASE).as('createQuizExercise');
        cy.get('#quiz-save').click();
        return cy.wait('@createQuizExercise');
    }

    import() {
        cy.intercept(POST, `${QUIZ_EXERCISE_BASE}/import/*`).as('quizExerciseImport');
        cy.get('#quiz-save').click();
        return cy.wait('@quizExerciseImport');
    }
}
