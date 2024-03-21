import { EXERCISE_BASE, MODELING_EDITOR_CANVAS, POST } from '../../../constants';

export class DragAndDropQuiz {
    createDnDQuiz(title: string) {
        cy.get('#quiz-import-apollon-dnd-question').should('be.visible').click();
        cy.get('#create-apollon-diagram').should('be.visible').click();
        cy.get('#field_diagram_title').type(title);
        cy.get('#save-dnd-quiz').click();
        cy.get('#open-diagram').click();
    }

    dragItemIntoDragArea(itemIndex: number) {
        cy.get('#drag-item-' + itemIndex).drag('#drop-location');
    }

    setTitle(title: string) {
        cy.get('#field_title').type(title);
    }

    /**
     * Drags an element to the modeling editor canvas
     *
     * @param x drop location on X-axis
     * @param y drop location on Y-axis
     */
    dragUsingCoordinates(x: number, y: number) {
        cy.get('#modeling-editor-sidebar').children().eq(2).drag(MODELING_EDITOR_CANVAS, { target: { x, y } });
        cy.wait(200);
        cy.get(MODELING_EDITOR_CANVAS).trigger('pointerup');
    }

    /**
     * Returns minimum and maximum X value of drop location elements
     *
     * @param $els selected drop location elements
     */
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

    activateInteractiveMode() {
        const modelingEditorSidebar = cy.get('#modeling-editor-sidebar');
        const container = modelingEditorSidebar.children().eq(0);
        const interactiveButton = container.children().eq(1);
        interactiveButton.click();
    }

    markElementAsInteractive(nthElementOnCanvas: number, nthChildOfElement: number) {
        cy.get(MODELING_EDITOR_CANVAS).children().children().children().eq(nthElementOnCanvas).children().eq(0).children().eq(nthChildOfElement).click();
    }

    generateQuizExercise() {
        cy.get('#generate-quiz-exercise').click();
        cy.get('#quiz-save').should('be.visible');
        cy.wait(100);
        cy.get('#quiz-save').click();
    }

    waitForQuizExerciseToBeGenerated() {
        cy.get('#jhi-text-exercise-heading-edit').should('be.visible');
    }

    previewQuiz() {
        cy.get('#preview-quiz').click();
    }

    waitForQuizPreviewToLoad() {
        cy.get('.drag-and-drop-area').should('be.visible');
        cy.wait(200);
    }

    submit() {
        cy.intercept(POST, `${EXERCISE_BASE}/*/submissions/live`).as('createQuizExercise');
        cy.get('#submit-quiz').contains('Submit').click();
        return cy.wait('@createQuizExercise');
    }
}
