import { POST, BASE_API } from '../constants';

export class DragAndDropQuiz {
    dragItemIntoDragArea() {
        cy.get('.drag-items').children().eq(0).drag('.drop-location');
    }

    submit() {
        cy.intercept(POST, BASE_API + 'exercises/*/submissions/live').as('createQuizExercise');
        cy.get('.jhi-btn').contains('Submit').click();
        return cy.wait('@createQuizExercise');
    }
}
