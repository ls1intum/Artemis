export class OnlineEditorPage {
    constructor() {
        cy.intercept('POST', '/api/repository/*/**').as('createFile');
        cy.intercept('PUT', '/api/repository/*/files*').as('saveFiles');
    }

    findFileBrowser() {
        return cy.get('#cardFiles');
    }

    focusCodeEditor() {
        return cy.get('#ace-code-editor').find('.ace_content').click();
    }

    typeSubmission(submission: ProgrammingExerciseSubmission, packageName: string) {
        for (let newFile of submission.files) {
            cy.log(`Entering content for file ${newFile.name}`);
            // This works for the current solutions because all files are in the
            this.openFileWithName(newFile.name);
            this.focusCodeEditor().type('{selectall}{backspace}', { delay: 100 });
            cy.fixture(newFile.path).then(($fileContent) => {
                this.focusCodeEditor().type(this.sanitizeInput($fileContent, packageName) + '{shift}{pagedown}{backspace}');
            });
        }
    }

    /**
     * Makes sure that the input does not contain any characters, which might be recognized by cypress as special characters, and replaces all newlines with the cypress '{enter}' command.
     * Apparently this causes issues in the ace code editor if there is no space before a newline, so we add a space there as well.
     */
    private sanitizeInput(input: string, packageName: string) {
        return input.replace(/\${packageName}/g, packageName).replace(/{/g, '{{}');
    }

    openFileWithName(name: string) {
        return this.findFileBrowser().contains(name).click();
    }

    submit() {
        return cy.get('#submit_button').click();
    }

    save() {
        cy.get('#save_button').click();
        return cy.wait('@saveFiles');
    }

    createFileInRootPackage(fileName: string) {
        cy.get('.file-icons').children('button').first().click();
        cy.get('jhi-code-editor-file-browser-create-node').type(fileName).type('{enter}');
        cy.wait('@createFile');
        this.findFileBrowser().contains(fileName).should('be.visible').wait(500);
    }

    getResultPanel() {
        return cy.get('jhi-updating-result');
    }

    getInstructionsPanel() {
        return cy.get('#cardInstructions');
    }

    getInstructionSymbols() {
        return this.getInstructionsPanel().get('.stepwizard-row').find('.stepwizard-step');
    }

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
