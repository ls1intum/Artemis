/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Creation Page.
 * Path: /course-management/{courseID}/modeling-exercises/{exerciseID}
 */
export class CreateModelingExercisePage {
    MODELING_SPACE = '.sc-furvIG';
    COMPONENT_CONTAINER = '.sc-ksdxAp';

    setTitle(title: string) {
        cy.get('#field_title').type(title);
    }

    setCategories(categories: string[]) {
        categories.forEach((category) => {
            cy.get('#field_categories').type(category);
            // this line is a hack so the category ends
            cy.get('#id').click({force: true});
        });
    }

    setPoints(points: number) {
        cy.get('#field_points').type(points.toString());
    }

    save(): any {
        cy.contains('Save').click();
    }

    /**
     * Adds a Modeling Component to the Example Solution
     * */
    addComponentToExampleSolution(componentNumber: number) {
        cy.get(`${this.COMPONENT_CONTAINER} > :nth-child(${componentNumber}) > :nth-child(1) > :nth-child(1)`)
            .drag(`${this.MODELING_SPACE}`, { position: 'bottomLeft', force: true });
    }
}
