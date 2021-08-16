/**
 * A class which encapsulates UI selectors and actions for the text exercise page.
 */
export class TextEditor {
    /**
     * Types the specified string into the text field.
     * @param submission the submission that should be typed into the textfield
     */
    typeSubmission(submission: string) {
        cy.get('#text-editor-tab').type(submission, { parseSpecialCharSequences: false });
    }
}
