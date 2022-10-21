import { POST, EXERCISE_BASE } from '../../../constants';

export class DragAndDropQuiz {
    dragItemIntoDragArea(itemIndex: number) {
        cy.get('#drag-item-' + itemIndex).drag('#drop-location');
    }

    submit() {
        cy.intercept(POST, EXERCISE_BASE + '*/submissions/live').as('createQuizExercise');
        cy.get('#submit-quiz').contains('Submit').click();
        return cy.wait('@createQuizExercise');
    }
}
