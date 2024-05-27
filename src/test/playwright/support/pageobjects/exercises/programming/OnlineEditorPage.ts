import { Page, expect } from '@playwright/test';
import { BASE_API } from '../../../constants';
import { getExercise } from '../../../utils';
import { Fixtures } from '../../../../fixtures/fixtures';

export class OnlineEditorPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    findFileBrowser(exerciseID: number) {
        return getExercise(this.page, exerciseID).locator('#cardFiles');
    }

    findEditorTextField(exerciseID: number) {
        return getExercise(this.page, exerciseID).locator('.view-lines').first();
    }

    async typeSubmission(exerciseID: number, submission: ProgrammingExerciseSubmission) {
        for (const newFile of submission.files) {
            if (newFile.directory || submission.sourceDirectory) {
                await this.createFileInFolder(exerciseID, newFile.name, newFile.directory ?? submission.sourceDirectory);
            } else {
                await this.createFileInRootFolder(exerciseID, newFile.name);
            }
            const fileContent = await Fixtures.get(newFile.fixturePath);
            const editorElement = this.findEditorTextField(exerciseID);
            await editorElement.click();
            await editorElement.evaluate(
                (element, { fileContent }) => {
                    const clipboardData = new DataTransfer();
                    const format = 'text/plain';
                    clipboardData.setData(format, fileContent);
                    const event = new ClipboardEvent('paste', { clipboardData });
                    element.dispatchEvent(event);
                },
                { fileContent: fileContent! },
            );
            await this.page.waitForTimeout(500);
        }
        await this.page.waitForTimeout(500);
    }

    async deleteFile(exerciseID: number, name: string) {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/repository/*/**`);
        await this.findFile(exerciseID, name).locator('#file-browser-file-delete').click();
        await this.page.locator('#delete-file').click();
        const response = await responsePromise;
        expect(response.status()).toBe(200);
        await expect(this.findFile(exerciseID, name)).not.toBeVisible();
    }

    private findFile(exerciseID: number, name: string) {
        const filenameElement = this.page.locator('.list-group-item__fileName').getByText(name, { exact: true });
        return this.findFileBrowser(exerciseID).locator('#file-browser-file', { has: filenameElement });
    }

    async openFileWithName(exerciseID: number, name: string) {
        await this.findFile(exerciseID, name).click();
        await this.page.waitForTimeout(2000);
    }

    async submit(exerciseID: number) {
        await getExercise(this.page, exerciseID).locator('#submit_button').click();
        await expect(getExercise(this.page, exerciseID).locator('#result-score-badge', { hasText: 'GRADED' })).toBeVisible({ timeout: 200000 });
    }

    async submitPractice(exerciseID: number) {
        await getExercise(this.page, exerciseID).locator('#submit_button').click();
        await expect(getExercise(this.page, exerciseID).locator('#result-score-badge', { hasText: 'PRACTICE' })).toBeVisible({ timeout: 200000 });
    }

    async createFileInRootFolder(exerciseID: number, fileName: string) {
        await getExercise(this.page, exerciseID).locator('[id="create_file_root"]').click();
        await this.page.waitForTimeout(500);
        await this.createFile(exerciseID, fileName);
    }

    async createFileInFolder(exerciseID: number, fileName: string, folderPath?: string) {
        // Find approopriate folder
        await getExercise(this.page, exerciseID).locator('#file-browser-folder-create-file').last().click();
        await this.page.waitForTimeout(500);
        await this.createFile(exerciseID, fileName, folderPath);
    }

    private async createFile(exerciseID: number, fileName: string, folderPath?: string) {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/repository/*/file?file=${folderPath ?? ''}${fileName}`);
        await getExercise(this.page, exerciseID).locator('#file-browser-create-node').pressSequentially(fileName);
        await this.page.waitForTimeout(500);
        await getExercise(this.page, exerciseID).locator('#file-browser-create-node').press('Enter');
        const response = await responsePromise;
        expect(response.status()).toBe(200);
        await expect(this.findFileBrowser(exerciseID).filter({ hasText: fileName })).toBeVisible();
        await this.page.waitForTimeout(500);
    }

    async getResultPanel() {
        return this.page.locator('#result');
    }

    async getResultScore() {
        const resultScore = this.page.locator('#result-score');
        await resultScore.waitFor({ state: 'visible' });
        return resultScore;
    }

    getResultScoreFromExercise(exerciseID: number) {
        return getExercise(this.page, exerciseID).locator('#result-score');
    }

    async getBuildOutput() {
        return this.page.locator('#cardBuildOutput');
    }

    async toggleCompressFileTree(exerciseID: number) {
        await getExercise(this.page, exerciseID).locator('#compress_tree').click();
    }

    async makeSubmissionAndVerifyResults(exerciseID: number, submission: ProgrammingExerciseSubmission, verifyOutput: () => Promise<void>) {
        // Decompress the file tree to access the parent folder
        await this.toggleCompressFileTree(exerciseID);
        // We delete all existing files, so we can create new files and don't have to delete their already existing content
        for (const deleteFile of submission.deleteFiles) {
            await this.deleteFile(exerciseID, deleteFile);
        }
        await this.typeSubmission(exerciseID, submission);
        await this.submit(exerciseID);
        await verifyOutput();
    }
}

/**
 * A class which encapsulates a programming exercise submission taken from the k6 resources.
 *
 * @param files An array of containers, which contain the file path of the changed file as well as its name.
 * @param deleteFiles An array of file names which should be deleted before the submission.
 * @param expectedResult The expected result of the submission.
 * @param sourceDirectory The optional source directory in which the files should be placed.
 * If not set, the files will be placed in the root directory.
 */
export class ProgrammingExerciseSubmission {
    deleteFiles: string[];
    files: ProgrammingExerciseFile[];
    expectedResult: string;
    sourceDirectory?: string;
}

/**
 * A class which encapsulates a programming exercise file.
 *
 * @param name The name of the file.
 * @param directory The optional directory in which the file should be placed.
 * If not set, the file will be placed in the root directory.
 * @param fixturePath The path to the fixture file.
 */
class ProgrammingExerciseFile {
    name: string;
    directory?: string;
    fixturePath: string;
}
