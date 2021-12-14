import { POST, EXERCISE_BASE } from '../../../constants';

export class DragAndDropQuiz {
    dragItemIntoDragArea() {
        cy.get('#drag-items').children().eq(0).drag('.drop-location');
    }

    submit() {
        cy.intercept(POST, EXERCISE_BASE + '*/submissions/live').as('createQuizExercise');
        cy.get('#submit-quiz').contains('Submit').click();
        return cy.wait('@createQuizExercise');
    }
}
