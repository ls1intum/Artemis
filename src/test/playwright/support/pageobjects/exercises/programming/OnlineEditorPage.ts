import { Page, expect } from '@playwright/test';
import { BASE_API } from '../../../constants';
import { getExercise } from '../../../utils';
import { Commands } from '../../../commands';
import { UserCredentials } from '../../../users';
import { CoursesPage } from '../../course/CoursesPage';
import { CourseOverviewPage } from '../../course/CourseOverviewPage';
import { Fixtures } from '../../../../fixtures/fixtures';

export class OnlineEditorPage {
    private readonly page: Page;
    private readonly courseList: CoursesPage;
    private readonly courseOverview: CourseOverviewPage;

    constructor(page: Page, courseList: CoursesPage, courseOverview: CourseOverviewPage) {
        this.page = page;
        this.courseList = courseList;
        this.courseOverview = courseOverview;
    }

    findFileBrowser(exerciseID: number) {
        return getExercise(this.page, exerciseID).locator('#cardFiles');
    }

    findEditorTextField(exerciseID: number) {
        return getExercise(this.page, exerciseID).locator('.view-lines').first();
    }

    async typeSubmission(exerciseID: number, submission: ProgrammingExerciseSubmission) {
        for (const newFile of submission.files) {
            if (submission.createFilesInRootFolder) {
                await this.createFileInRootFolder(exerciseID, newFile.name);
            } else {
                await this.createFileInRootPackage(exerciseID, newFile.name, submission.packageName!);
            }
            const fileContent = await Fixtures.get(newFile.path);
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
        return this.findFileBrowser(exerciseID).locator('#file-browser-file', { hasText: name });
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
        const responsePromise = this.page.waitForResponse(`${BASE_API}/repository/*/file?file=${fileName}`);
        await getExercise(this.page, exerciseID).locator('#file-browser-create-node').pressSequentially(fileName);
        await this.page.waitForTimeout(500);
        await getExercise(this.page, exerciseID).locator('#file-browser-create-node').press('Enter');
        const response = await responsePromise;
        expect(response.status()).toBe(200);
        await expect(this.findFileBrowser(exerciseID).filter({ hasText: fileName })).toBeVisible();
        await this.page.waitForTimeout(500);
    }

    async createFileInRootPackage(exerciseID: number, fileName: string, packageName: string) {
        const packagePath = packageName.replace(/\./g, '/');
        const filePath = `src/${packagePath}/${fileName}`;
        await getExercise(this.page, exerciseID).locator('#file-browser-folder-create-file').nth(2).click();
        await this.page.waitForTimeout(500);
        const responsePromise = this.page.waitForResponse(`${BASE_API}/repository/*/file?file=${filePath}`);
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
        await Commands.reloadUntilFound(this.page, '#result-score');
        return this.page.locator('#result-score');
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

    async startParticipation(courseId: number, exerciseId: number, credentials: UserCredentials) {
        await Commands.login(this.page, credentials, '/');
        await this.page.waitForURL(/\/courses/);
        await this.courseList.openCourse(courseId!);
        await this.courseOverview.startExercise(exerciseId);
        await Commands.reloadUntilFound(this.page, '#open-exercise-' + exerciseId);
        await this.courseOverview.openRunningProgrammingExercise(exerciseId);
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
