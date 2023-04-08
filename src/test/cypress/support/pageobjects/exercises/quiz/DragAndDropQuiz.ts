import { EXERCISE_BASE, POST } from '../../../constants';
import { MODELING_EDITOR_CANVAS } from '../modeling/ModelingEditor';

export class DragAndDropQuiz {
    dragItemIntoDragArea(itemIndex: number) {
        cy.get('#drag-item-' + itemIndex).drag('#drop-location');
    }

    setTitle(title: string) {
        cy.get('#quiz-title').type(title);
    }

    dragUsingCoordinates(x: number, y: number) {
        // @ts-ignore https://github.com/4teamwork/cypress-drag-drop/issues/103
        cy.get('#modeling-editor-sidebar').children().eq(2).drag(MODELING_EDITOR_CANVAS, { target: { x, y } });
        cy.wait(200);
        cy.get(MODELING_EDITOR_CANVAS).trigger('pointerup');
    }

    getXAxis($els: JQuery<HTMLElement>) {
        let minX = Number.MAX_SAFE_INTEGER;
        let maxX = Number.MIN_SAFE_INTEGER;
        $els.each((index, el) => {
            const rect = el.getBoundingClientRect();
            if (rect.x < minX) {
                minX = rect.x;
            }
            if (rect.x + rect.width > maxX) {
                maxX = rect.x + rect.width;
            }
        });
        return { minX, maxX };
    }

    submit() {
        cy.intercept(POST, EXERCISE_BASE + '*/submissions/live').as('createQuizExercise');
        cy.get('#submit-quiz').contains('Submit').click();
        return cy.wait('@createQuizExercise');
    }
}
