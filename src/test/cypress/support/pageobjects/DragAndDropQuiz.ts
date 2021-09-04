import { POST } from '../constants';

export class DragAndDropQuiz {
    dragItemIntoDragArea() {
        cy.get('.drag-items').children().eq(0).drag('.drop-location', {position: 'center'});
    }

    submit() {
        cy.intercept(POST, '/api/exercises/*/submissions/live').as('createQuizExercise');
        cy.get('.jhi-btn').contains('Submit').click();
        return cy.wait('@createQuizExercise');
    }
}
