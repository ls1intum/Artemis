/**
 * A class which encapsulates UI selectors and actions for the file upload exercise example submissions page.
 */
export class FileUploadExerciseExampleSubmissionsPage {
    clickCreateExampleSubmission() {
        cy.get('#create-example-submission').click();
    }
}
