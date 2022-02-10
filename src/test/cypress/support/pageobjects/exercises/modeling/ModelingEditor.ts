import { BASE_API, PUT } from '../../../constants';
import scrollBehaviorOptions = Cypress.scrollBehaviorOptions;

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

    submit() {
        cy.intercept(PUT, BASE_API + 'exercises/*/modeling-submissions').as('createModelingSubmission');
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
