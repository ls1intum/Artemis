import { BASE_API, PUT } from '../constants';

/**
 * This provides functions for interacting with the modeling editor
 * */
export class ModelingEditor {
    MODELING_SPACE = '.sc-furvIG';
    COMPONENT_CONTAINER = '.sc-ksdxAp';

    /**
     * Adds a Modeling Component to the Example Solution
     * */
    addComponentToModel(componentNumber: number) {
        cy.get(`${this.COMPONENT_CONTAINER} > :nth-child(${componentNumber}) > :nth-child(1) > :nth-child(1)`).drag(`${this.MODELING_SPACE}`, {
            position: 'bottomLeft',
            force: true,
        });
    }

    submit() {
        cy.intercept(PUT, BASE_API + 'exercises/*/modeling-submissions').as('createModelingSubmission');
        cy.get('.btn-primary').first().click();
        cy.wait('@createModelingSubmission');
    }
}
