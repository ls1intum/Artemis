import { MODELING_SPACE } from './ModelingEditor';

// TODO: find or create better selectors for this
const FEEDBACK_CONTAINER = '.sc-lcuiOb';
/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Assessment editor
 */
export class ModelingExerciseAssessmentEditor {
    openAssessmentForComponent(componentNumber: number) {
        cy.get('.apollon-row').getSettled(`${MODELING_SPACE} >> :nth-child(${componentNumber})`).children().eq(0).dblclick('top', { force: true });
    }

    assessComponent(points: number, feedback: string) {
        cy.get(`${FEEDBACK_CONTAINER} > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)`).type(`${points}`);
        cy.get(`${FEEDBACK_CONTAINER} > :nth-child(1) > :nth-child(3)`).type(`${feedback}`);
    }

    addNewFeedback(points: number, feedback?: string) {
        cy.get('.btn').contains('Add new Feedback').click();
        cy.get('.col-lg-6 >>>> :nth-child(1) > :nth-child(2)').clear().type(points.toString());
        if (feedback) {
            cy.get('.col-lg-6 >>>> :nth-child(2) > :nth-child(2)').type(feedback);
        }
    }
}
