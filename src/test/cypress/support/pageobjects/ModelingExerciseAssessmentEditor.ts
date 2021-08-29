import { AbstractExerciseAssessmentPage } from './assessment/AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Assessment editor
 */
export class ModelingExerciseAssessmentEditor extends AbstractExerciseAssessmentPage {
    openAssessmentForComponent(componentNumber: number) {
        cy.get('.apollon-row').getSettled(`.sc-furvIG >> :nth-child(${componentNumber})`).children().eq(0).dblclick('top', { force: true });
    }

    assessComponent(points: number, feedback: string) {
        cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type(`${points}`);
        cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(3)').type(`${feedback}`);
    }
}
