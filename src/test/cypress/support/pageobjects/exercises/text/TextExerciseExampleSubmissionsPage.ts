/**
 * A class which encapsulates UI selectors and actions for the text exercise example submissions page.
 */
export class TextExerciseExampleSubmissionsPage {
    clickCreateExampleSubmission() {
        cy.get('#create-example-submission').click();
    }
}
