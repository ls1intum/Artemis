const buildingAndTesting = 'Building and testing...';

/**
 * A class which encapsulates UI selectors and actions for the Online Editor Page.
 */
export class OnlineEditorPage {
    /**
     * @returns the root element of the file browser. Useful for further querying.
     */
    findFileBrowser() {
        return cy.get('#cardFiles');
    }

    /**
     * Focuses the code editor content to allow typing into it.
     */
    focusCodeEditor() {
        return cy.get('#ace-code-editor').find('.ace_content').click();
    }

    /**
     * Writes all the content in the corresponding files in the online editor. NOTE: This does not create non existing files.
     * It only opens existing files and writes the content there!
     * @param submission object which contains the information about which files need to be edited with what content
     * @param packageName the package name of the project to overwrite it in the submission templates
     */
    typeSubmission(submission: ProgrammingExerciseSubmission, packageName: string) {
        for (const newFile of submission.files) {
            this.createFileInRootPackage(newFile.name);
            cy.fixture(newFile.path).then(($fileContent) => {
                const sanitizedContent = this.sanitizeInput($fileContent, packageName);
                this.focusCodeEditor().type(sanitizedContent, { delay: 3 });
                // Delete the remaining content which has been automatically added by the code editor.
                // We simply send as many {del} keystrokes as the file has characters. This shouldn't increase the test runtime by too long since we set the delay to 0.
                const deleteRemainingContent = '{del}'.repeat(sanitizedContent.length);
                cy.focused().type(deleteRemainingContent, { delay: 0 });
            });
        }
    }

    /**
     * Makes sure that the input does not contain any characters, which might be recognized by cypress as special characters,
     * and replaces all newlines with the cypress '{enter}' command.
     * Apparently this causes issues in the ace code editor if there is no space before a newline, so we add a space there as well.
     */
    private sanitizeInput(input: string, packageName: string) {
        return input.replace(/\${packageName}/g, packageName).replace(/{/g, '{{}');
    }

    /**
     * Deletes a file in the filebrowser.
     * @param name the file name
     */
    deleteFile(name: string) {
        cy.intercept('DELETE', '/api/repository/*/**').as('deleteFile');
        this.findFile(name).find('[data-icon="trash"]').click();
        cy.get('[jhitranslate="artemisApp.editor.fileBrowser.delete"]').click();
        cy.wait('@deleteFile').its('response.statusCode').should('eq', 200);
        this.findFileBrowser().contains(name).should('not.exist');
    }

    /**
     * @param name the file name
     * @returns the root element of a file in the filebrowser
     */
    private findFile(name: string) {
        return this.findFileBrowser().contains(name).parent();
    }

    /**
     * Opens a file in the file browser by clicking on it.
     */
    openFileWithName(name: string) {
        this.findFile(name).click().wait(2000);
    }

    /**
     * Submits the currently saved files by clicking on the submit button.
     */
    submit() {
        cy.get('#submit_button').click();
        this.getResultPanel().contains(buildingAndTesting, { timeout: 15000 }).should('be.visible');
        this.getBuildOutput().contains(buildingAndTesting).should('be.visible');
        this.getResultPanel().contains('GRADED', { timeout: 80000 }).should('be.visible');
    }

    /**
     * Creates a file at root level (in the main package) in the file browser.
     * @param fileName the name of the new file
     */
    createFileInRootPackage(fileName: string) {
        cy.intercept('POST', '/api/repository/*/**').as('createFile');
        cy.get('.file-icons').children('button').first().click();
        cy.get('jhi-code-editor-file-browser-create-node').type(fileName).type('{enter}');
        cy.wait('@createFile');
        this.findFileBrowser().contains(fileName).should('be.visible').wait(500);
    }

    /**
     * @returns the root element of the result panel. This can be used for further querying inside this panel
     */
    getResultPanel() {
        return cy.get('jhi-updating-result');
    }

    /**
     * @returns the root element of the panel on the right, which shows all instructions
     */
    getInstructionsPanel() {
        return cy.get('#cardInstructions');
    }

    /**
     * @returns returns all instruction symbols. Each test has one instruction symbol with its state (questionmark, cross or checkmark)
     */
    getInstructionSymbols() {
        return this.getInstructionsPanel().get('.stepwizard-row').find('.stepwizard-step');
    }

    /**
     * @returns the root element of the panel, which shows the CI build output.
     */
    getBuildOutput() {
        return cy.get('#cardBuildOutput');
    }
}

/**
 * A class which encapsulates a programming exercise submission taken from the k6 resources.
 *
 * @param files An array of containers, which contain the file path of the changed file as well as its name.
 */
export class ProgrammingExerciseSubmission {
    files: ProgrammingExerciseFile[];
}

class ProgrammingExerciseFile {
    name: string;
    path: string;
}
