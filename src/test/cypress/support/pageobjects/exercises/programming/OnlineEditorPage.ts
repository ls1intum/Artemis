import { DELETE } from '../../../constants';
import { artemis } from '../../../ArtemisTesting';
import { GET, BASE_API, POST } from '../../../constants';
import { CypressCredentials } from '../../../users';

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
        // The ace editor is an external element, so we can't freely add ids here
        return cy.get('#ace-code-editor').find('.ace_content').click();
    }

    /**
     * Writes all the content in the corresponding files in the online editor. NOTE: This does not create non-existing files.
     * It only opens existing files and writes the content there!
     * @param submission object which contains the information about which files need to be edited with what content
     * @param packageName the package name of the project to overwrite it in the submission templates
     */
    typeSubmission(submission: ProgrammingExerciseSubmission, packageName: string) {
        for (const newFile of submission.files) {
            this.createFileInRootPackage(newFile.name, packageName);
            cy.fixture(newFile.path).then(($fileContent) => {
                const sanitizedContent = this.sanitizeInput($fileContent, packageName);
                this.focusCodeEditor().type(sanitizedContent, { delay: 8 });
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
        cy.intercept(DELETE, BASE_API + 'repository/*/**').as('deleteFile');
        this.findFile(name).find('#file-browser-file-delete').click();
        cy.get('#delete-file').click();
        cy.wait('@deleteFile').its('response.statusCode').should('eq', 200);
        this.findFileBrowser().contains(name).should('not.exist');
    }

    /**
     * @param name the file name
     * @returns the root element of a file in the filebrowser
     */
    private findFile(name: string) {
        return this.findFileBrowser().contains(name).parents('#file-browser-file');
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
        cy.get('#result-score-graded', { timeout: 140000 }).should('contain.text', 'GRADED').and('be.visible');
    }

    /**
     * Creates a file at root level (in the main package) in the file browser.
     * @param fileName the name of the new file (e.g. "Policy.java")
     * @param packageName the name of the package (e.g. "de.test")
     */
    createFileInRootPackage(fileName: string, packageName: string) {
        const packagePath = packageName.replace(/\./g, '/');
        const filePath = `src/${packagePath}/${fileName}`;
        const postRequestId = 'createFile' + fileName;
        const getRequestId = 'getFile' + fileName;
        const requestPath = BASE_API + 'repository/*/file?file=' + filePath;
        cy.get('[id="file-browser-folder-create-file"]').eq(2).click().wait(500);
        cy.intercept(POST, requestPath).as(postRequestId);
        cy.intercept(GET, requestPath).as(getRequestId);
        cy.get('#file-browser-create-node').type(fileName).wait(500).type('{enter}');
        cy.wait('@' + postRequestId)
            .its('response.statusCode')
            .should('eq', 200);
        cy.wait('@' + getRequestId)
            .its('response.statusCode')
            .should('eq', 200);
        this.findFileBrowser().contains(fileName).should('be.visible').wait(500);
    }

    /**
     * @returns the root element of the result panel. This can be used for further querying inside this panel
     */
    getResultPanel() {
        return cy.get('#result');
    }

    /**
     * @returns the element containing the result score percentage.
     */
    getResultScore() {
        let resultScore = cy.get('#result-score');
        if (!resultScore) {
            resultScore = cy.get('#result-score-no-feedback');
        }
        return resultScore;
    }

    /**
     * @returns the root element of the panel, which shows the CI build output.
     */
    getBuildOutput() {
        return cy.get('#cardBuildOutput');
    }

    toggleCompressFileTree() {
        return cy.get('#compress_tree').click();
    }
}

/**
 * General method for entering, submitting and verifying something in the online editor.
 */
export function makeSubmissionAndVerifyResults(editorPage: OnlineEditorPage, packageName: string, submission: ProgrammingExerciseSubmission, verifyOutput: () => void) {
    // Decompress the file tree to access the parent folder
    editorPage.toggleCompressFileTree();
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
export function startParticipationInProgrammingExercise(courseId: number, exerciseId: number, credentials: CypressCredentials) {
    const courseOverview = artemis.pageobjects.course.overview;
    const courses = artemis.pageobjects.course.list;
    cy.login(credentials, '/');
    cy.url().should('include', '/courses');
    cy.log('Participating in the programming exercise as a student...');
    courses.openCourse(courseId!);
    cy.url().should('include', '/exercises');
    courseOverview.startExercise(exerciseId);
    courseOverview.openRunningProgrammingExercise(exerciseId);
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
