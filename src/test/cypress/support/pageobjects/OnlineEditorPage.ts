import { COURSE_BASE } from './../requests/CourseManagementRequests';
import { GET, BASE_API, POST } from './../constants';
import { CypressCredentials } from '../users';

const buildingAndTesting = 'Building and testing...';
const exerciseRow = '.course-exercise-row';

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
        cy.wait(1000);
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
        this.getResultPanel().contains('GRADED', { timeout: 140000 }).should('be.visible');
    }

    /**
     * Creates a file at root level (in the main package) in the file browser.
     * @param fileName the name of the new file
     */
    createFileInRootPackage(fileName: string) {
        cy.intercept(POST, BASE_API + 'repository/*/**').as('createFile');
        cy.get('.file-icons').children('button').first().click().wait(500);
        cy.get('jhi-code-editor-file-browser-create-node').type(fileName).wait(500).type('{enter}');
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
 * General method for entering, submitting and verifying something in the online editor.
 */
export function makeSubmissionAndVerifyResults(editorPage: OnlineEditorPage, packageName: string, submission: ProgrammingExerciseSubmission, verifyOutput: () => void) {
    // We create an empty file so that the file browser does not create an extra subfolder when all files are deleted
    editorPage.createFileInRootPackage('placeholderFile');
    // We delete all existing files, so we can create new files and don't have to delete their already existing content
    editorPage.deleteFile('Client.java');
    editorPage.deleteFile('BubbleSort.java');
    editorPage.deleteFile('MergeSort.java');
    editorPage.typeSubmission(submission, packageName);
    editorPage.submit();
    verifyOutput();
}

/**
 * Starts the participation in the test programming exercise.
 */
export function startParticipationInProgrammingExercise(courseName: string, programmingExerciseName: string, credentials: CypressCredentials) {
    cy.login(credentials, '/');
    cy.url().should('include', '/courses').wait(500);
    cy.log('Participating in the programming exercise as a student...');
    cy.contains(courseName).parents('.card-header').click().wait(1000);
    cy.url().should('include', '/exercises');
    cy.intercept(POST, COURSE_BASE + '*/exercises/*/participations').as('participateInExerciseQuery');
    cy.get(exerciseRow).contains(programmingExerciseName).should('be.visible');
    cy.get(exerciseRow).find('.start-exercise').click();
    cy.wait('@participateInExerciseQuery');
    cy.intercept(GET, BASE_API + 'programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks').as('initialQuery');
    cy.get(exerciseRow).find('[buttonicon="folder-open"]').click();
    cy.wait('@initialQuery');
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
