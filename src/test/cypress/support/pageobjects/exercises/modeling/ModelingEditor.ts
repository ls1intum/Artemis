import { BASE_API, PUT } from '../../../constants';
import scrollBehaviorOptions = Cypress.scrollBehaviorOptions;

// TODO: find or create better selectors for modeling objects
export const MODELING_EDITOR_CANVAS = '#modeling-editor-canvas';

/**
 * This provides functions for interacting with the modeling editor
 * */
export class ModelingEditor {
    /**
     * Adds a Modeling Component to the Example Solution
     * */
    addComponentToModel(componentNumber: number, scrollBehavior: scrollBehaviorOptions = 'center') {
        cy.get('#modeling-editor-sidebar').children().eq(componentNumber).drag(`${MODELING_EDITOR_CANVAS}`, { scrollBehavior, timeout: 1000 });
    }

    save() {
        cy.intercept(PUT, BASE_API + 'exercises/*/modeling-submissions').as('createModelingSubmission');
        cy.contains('Save').click();
        return cy.wait('@createModelingSubmission');
    }

    submit() {
        cy.intercept(PUT, BASE_API + 'exercises/*/modeling-submissions').as('createModelingSubmission');
        cy.get('.btn-primary').first().click();
        return cy.wait('@createModelingSubmission');
    }

    clickCreateNewExampleSubmission() {
        cy.get('[jhitranslate="artemisApp.modelingExercise.createNewExampleSubmission"]').click();
    }

    clickCreateExampleSubmission() {
        cy.get('[jhitranslate="artemisApp.modelingExercise.createExampleSubmission"]').click();
    }

    showExampleAssessment() {
        cy.get('[jhitranslate="artemisApp.modelingExercise.showExampleAssessment"]').click();
    }
}
