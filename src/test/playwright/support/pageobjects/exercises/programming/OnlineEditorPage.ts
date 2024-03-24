import { Page, expect } from '@playwright/test';
import { BASE_API, ExerciseCommit } from '../../../constants';
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

    async typeSubmission(exerciseID: number, submission: ProgrammingExerciseSubmission) {
        for (const newFile of submission.files) {
            if (submission.createFilesInRootFolder) {
                await this.createFileInRootFolder(exerciseID, newFile.name);
            } else {
                await this.createFileInRootPackage(exerciseID, newFile.name, submission.packageName!);
            }
            const fileContent = await Fixtures.get(newFile.path);
            await this.page.evaluate(
                ({ editorSelector, fileContent }) => {
                    const editorElement = document.querySelector(editorSelector);
                    if (editorElement) {
                        // @ts-expect-error ace does not exists on windows, but works without issue
                        const editor = ace.edit(editorElement);
                        editor.setValue(fileContent, 1); // Set the editor's content
                    }
                },
                { editorSelector: '#ace-code-editor', fileContent: fileContent! },
            );
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
        await this.page.locator('.tab-bar-exercise-details').locator('#result-score').waitFor({ state: 'visible' });
        return this.page.locator('.tab-bar-exercise-details').locator('#result-score');
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
    }

    async openCodeEditor(exerciseId: number) {
        await Commands.reloadUntilFound(this.page, '#open-exercise-' + exerciseId);
        await this.courseOverview.openRunningProgrammingExercise(exerciseId);
    }

    async getRepoUrl() {
        const cloneRepoSelector = '.clone-repository';
        await Commands.reloadUntilFound(this.page, cloneRepoSelector);
        await this.page.locator(cloneRepoSelector).click();
        await this.page.locator('.popover-body').waitFor({ state: 'visible' });
        return await this.page.locator('.clone-url').innerText();
    }

    async openRepository() {
        const repositoryPage = this.page.context().waitForEvent('page');
        await this.page.locator('a', { hasText: 'Open repository' }).click();
        return await repositoryPage;
    }

    async openCommitHistory(repositoryPage: Page) {
        await repositoryPage.locator('a', { hasText: 'Open Commit History' }).click();
    }

    async checkCommit(repositoryPage: Page, message: string, result?: string, commits?: ExerciseCommit[]) {
        const commitHistory = repositoryPage.locator('.card-body', { hasText: 'Commit History' });
        // await expect(commitHistory.locator('td').getByText(message)).toBeVisible();
        // if (result) {
        //     await expect(commitHistory.locator('#result-score', { hasText: result })).toBeVisible();
        // } else {
        //     await expect(commitHistory.locator('td', { hasText: 'No result' })).toBeVisible();
        // }

        if (commits) {
            const commitCount = commits.length;
            for (let index = 0; index < commitCount; index++) {
                const commit = commits[index];
                const commitRow = commitHistory.locator('tbody').locator('tr').nth(index);
                await expect(commitRow.locator('td').getByText(commit.message)).toBeVisible();
                if (commit.result) {
                    await expect(commitRow.locator('#result-score', { hasText: commit.result })).toBeVisible();
                } else {
                    await expect(commitRow.locator('td', { hasText: 'No result' })).toBeVisible();
                }
            }
        }
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
