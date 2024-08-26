import { courseList, courseOverview } from '../../../artemis';
import { DELETE } from '../../../constants';
import { BASE_API, POST } from '../../../constants';
import { CypressCredentials } from '../../../users';
import { getExercise } from '../../../utils';

/**
 * A class which encapsulates UI selectors and actions for the Online Editor Page.
 */
export class OnlineEditorPage {
    /**
     * @param exerciseID the ID of the exercise
     * @returns the root element of the file browser. Useful for further querying.
     */
    findFileBrowser(exerciseID: number) {
        return getExercise(exerciseID).find('#cardFiles');
    }

    /**
     * Writes all the content in the corresponding files in the online editor. NOTE: This does not create non-existing files.
     * It only opens existing files and writes the content there!
     * @param exerciseID the ID of the exercise
     * @param submission object which contains the information about which files need to be edited with what content
     */
    typeSubmission(exerciseID: number, submission: ProgrammingExerciseSubmission) {
        for (const newFile of submission.files) {
            if (submission.createFilesInRootFolder) {
                this.createFileInRootFolder(exerciseID, newFile.name);
            } else {
                this.createFileInRootPackage(exerciseID, newFile.name, submission.packageName!);
            }
            cy.fixture(newFile.path)
                .then(($fileContent) => {
                    const clipboardData = new DataTransfer();
                    const format = 'text/plain';
                    clipboardData.setData(format, $fileContent);
                    const pasteEvent = new ClipboardEvent('paste', { clipboardData });
                    const editorElement = getExercise(exerciseID).find('.view-lines').first();
                    editorElement
                        .click()
                        .then((element) => {
                            element[0].dispatchEvent(pasteEvent);
                        })
                        .wait(500);
                })
                .wait(500);
        }
        cy.wait(500);
    }

    /**
     * Deletes a file in the filebrowser.
     * @param exerciseID the ID of the exercise
     * @param name the file name
     */
    deleteFile(exerciseID: number, name: string) {
        cy.intercept(DELETE, `${BASE_API}/repository/*/**`).as('deleteFile');
        this.findFile(exerciseID, name).find('#file-browser-file-delete').click();
        cy.get('#delete-file').click();
        cy.wait('@deleteFile').its('response.statusCode').should('eq', 200);
        this.findFileBrowser(exerciseID).contains(name).should('not.exist');
    }

    /**
     * @param exerciseID the ID of the exercise
     * @param name the file name
     * @returns the root element of a file in the filebrowser
     */
    private findFile(exerciseID: number, name: string) {
        return this.findFileBrowser(exerciseID).contains(name).parents('#file-browser-file');
    }

    /**
     * Opens a file in the file browser by clicking on it.
     */
    openFileWithName(exerciseID: number, name: string) {
        this.findFile(exerciseID, name).click().wait(2000);
    }

    /**
     * Submits the currently saved files by clicking on the submit button and expect GRADED result label.
     * @param exerciseID the ID of the exercise
     */
    submit(exerciseID: number) {
        getExercise(exerciseID).find('#submit_button').click();
        getExercise(exerciseID).find('#result-score-badge', { timeout: 200000 }).should('contain.text', 'GRADED').and('be.visible');
    }

    /**
     * Submits the currently saved files by clicking on the submit button and expect PRACTICE result label.
     * @param exerciseID the ID of the exercise
     */
    submitPractice(exerciseID: number) {
        getExercise(exerciseID).find('#submit_button').click();
        getExercise(exerciseID).find('#result-score-badge', { timeout: 200000 }).should('contain.text', 'PRACTICE').and('be.visible');
    }

    /**
     * Creates a file at root level in the file browser.
     * @param exerciseID the ID of the exercise
     * @param fileName the name of the new file (e.g. "Policy.java")
     */
    createFileInRootFolder(exerciseID: number, fileName: string) {
        const postRequestId = 'createFile' + fileName;
        const requestPath = `${BASE_API}/repository/*/file?file=${fileName}`;
        getExercise(exerciseID).find('[id="create_file_root"]').click().wait(500);
        cy.intercept(POST, requestPath).as(postRequestId);
        getExercise(exerciseID).find('#file-browser-create-node').type(fileName).wait(500).type('{enter}');
        cy.wait('@' + postRequestId)
            .its('response.statusCode')
            .should('eq', 200);
        this.findFileBrowser(exerciseID).contains(fileName).should('be.visible').wait(500);
    }

    /**
     * Creates a file at root level (in the main package) in the file browser.
     * @param exerciseID the ID of the exercise
     * @param fileName the name of the new file (e.g. "Policy.java")
     * @param packageName the name of the package (e.g. "de.test")
     */
    createFileInRootPackage(exerciseID: number, fileName: string, packageName: string) {
        const packagePath = packageName.replace(/\./g, '/');
        const filePath = `src/${packagePath}/${fileName}`;
        const postRequestId = 'createFile' + fileName;
        const requestPath = `${BASE_API}/repository/*/file?file=${filePath}`;
        getExercise(exerciseID).find('[id="file-browser-folder-create-file"]').eq(2).click().wait(500);
        cy.intercept(POST, requestPath).as(postRequestId);
        getExercise(exerciseID).find('#file-browser-create-node').type(fileName).wait(500).type('{enter}');
        cy.wait('@' + postRequestId)
            .its('response.statusCode')
            .should('eq', 200);
        this.findFileBrowser(exerciseID).contains(fileName).should('be.visible').wait(500);
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
        cy.reloadUntilFound('#result-score');
        return cy.get('#result-score');
    }

    /**
     * @param exerciseID the ID of the exercise
     * @returns the element containing the result score percentage.
     */
    getResultScoreFromExercise(exerciseID: number) {
        return getExercise(exerciseID).find('#result-score');
    }

    /**
     * @returns the root element of the panel, which shows the CI build output.
     */
    getBuildOutput() {
        return cy.get('#cardBuildOutput');
    }

    toggleCompressFileTree(exerciseID: number) {
        return getExercise(exerciseID).find('#compress_tree').click();
    }

    /**
     * General method for entering, submitting and verifying something in the online editor.
     */
    makeSubmissionAndVerifyResults(exerciseID: number, submission: ProgrammingExerciseSubmission, verifyOutput: () => void) {
        // Decompress the file tree to access the parent folder
        this.toggleCompressFileTree(exerciseID);
        // We delete all existing files, so we can create new files and don't have to delete their already existing content
        for (const deleteFile of submission.deleteFiles) {
            this.deleteFile(exerciseID, deleteFile);
        }
        this.typeSubmission(exerciseID, submission);
        this.submit(exerciseID);
        verifyOutput();
    }

    /**
     * Starts the participation in the test programming exercise.
     */
    startParticipation(courseId: number, exerciseId: number, credentials: CypressCredentials) {
        // For shorter intervals, the reload may come before the app can render the elements.
        const reloadInterval = 4000;
        cy.login(credentials, '/');
        cy.url().should('include', '/courses');
        cy.log('Participating in the programming exercise as a student...');
        courseList.openCourse(courseId!);
        cy.url().should('include', '/exercises');
        courseOverview.startExercise(exerciseId, reloadInterval);
        cy.reloadUntilFound('#open-exercise-' + exerciseId, reloadInterval);
        courseOverview.openRunningProgrammingExercise(exerciseId);
    }
}

/**
 * A class which encapsulates a programming exercise submission taken from the k6 resources.
 *
 * @param files An array of containers, which contain the file path of the changed file as well as its name.
 */
export class ProgrammingExerciseSubmission {
    deleteFiles: string[];
    createFilesInRootFolder: boolean;
    files: ProgrammingExerciseFile[];
    expectedResult: string;
    packageName?: string;
}

class ProgrammingExerciseFile {
    name: string;
    path: string;
}
