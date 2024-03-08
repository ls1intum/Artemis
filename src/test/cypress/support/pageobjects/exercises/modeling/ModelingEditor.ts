import { EXERCISE_BASE, MODELING_EDITOR_CANVAS, PUT } from '../../../constants';
import { getExercise } from '../../../utils';

import scrollBehaviorOptions = Cypress.scrollBehaviorOptions;

/**
 * This provides functions for interacting with the modeling editor
 * */
export class ModelingEditor {
    /**
     * Adds a Modeling Component to the Example Solution
     * */
    addComponentToModel(exerciseID: number, componentNumber: number, scrollBehavior: scrollBehaviorOptions = 'center', x?: number, y?: number) {
        // eslint-disable-next-line @typescript-eslint/ban-ts-comment
        // @ts-ignore https://github.com/4teamwork/cypress-drag-drop/issues/103
        getExercise(exerciseID)
            .find('#modeling-editor-sidebar')
            .children()
            .eq(componentNumber)
            .drag(`#exercise-${exerciseID} ${MODELING_EDITOR_CANVAS}`, { target: { x, y }, scrollBehavior, timeout: 1000 });
        getExercise(exerciseID).find(MODELING_EDITOR_CANVAS).trigger('pointerup');
    }

    getModelingCanvas() {
        return cy.get('#modeling-editor-canvas');
    }

    addComponentToExampleSolutionModel(componentNumber: number, scrollBehavior: scrollBehaviorOptions = 'center') {
        cy.get('#modeling-editor-sidebar').children().eq(componentNumber).drag(MODELING_EDITOR_CANVAS, { scrollBehavior, timeout: 1000 });
        cy.get(MODELING_EDITOR_CANVAS).trigger('mouseup').trigger('pointerup');
    }

    submit() {
        cy.intercept(PUT, `${EXERCISE_BASE}/*/modeling-submissions`).as('createModelingSubmission');
        cy.get('#submit-modeling-submission').first().click();
        return cy.wait('@createModelingSubmission');
    }

    clickCreateNewExampleSubmission() {
        cy.get('#new-modeling-example-submission').click();
    }

    clickCreateExampleSubmission() {
        cy.get('#create-example-submission').click();
    }

    showExampleAssessment() {
        cy.get('#show-modeling-example-assessment').click();
    }
}
