/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Assessment editor
 */
export class ModelingExerciseAssessmentEditor {

    openAssessmentForComponent(componentNumber: number) {
        cy.getSettled(`.sc-furvIG >> :nth-child(${componentNumber})`).dblclick('top', { force: true });
    }

    assessComponent(points: number, feedback: string) {
        cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type(`${points}`);
        cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(3)').type(`${feedback}`);
    }

    saveExampleAssessment() {
        cy.contains('Save Example Assessment').click();
    }
}
