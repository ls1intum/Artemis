/**
 * A class which encapsulates UI selectors and actions for the programming exercise creation page.
 */
export class ProgrammingExerciseCreationPage {
    /**
     * @param title the title of the programming exercise
     */
    setTitle(title: string) {
        cy.get('#field_title').clear().type(title);
    }

    /**
     * @param shortName the short name of the programming exercise
     */
    setShortName(shortName: string) {
        cy.get('#field_shortName').clear().type(shortName);
    }

    /**
     * @param packageName the package name of the programming exercise
     */
    setPackageName(packageName: string) {
        cy.get('#field_packageName').clear().type(packageName);
    }

    /**
     * @param points Achievable points in the exercise
     */
    setPoints(points: number) {
        cy.get('#field_points').clear().type(points.toString());
    }

    /**
     * Allows the usage of the online editor.
     */
    checkAllowOnlineEditor() {
        cy.get('#field_allowOnlineEditor').check();
    }

    /**
     * Generates the programming exercise.
     * @returns the chainable of the request to make further verifications
     */
    generate() {
        cy.intercept('POST', '/api/programming-exercises/setup').as('createProgrammingExercise');
        cy.get('#save-entity').click();
        return cy.wait('@createProgrammingExercise');
    }
}
