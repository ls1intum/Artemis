import { Page, expect } from '@playwright/test';
import { BASE_API } from '../../../constants';
import { getExercise, setMonacoEditorContentByLocator } from '../../../utils';
import { Fixtures } from '../../../../fixtures/fixtures';

export class OnlineEditorPage {
    private readonly page: Page;

    /**
     * Participation id observed in the editor's own repository requests, used to read back what was committed.
     * Captured rather than passed in so every caller of {@link makeSubmissionAndVerifyResults} gets the check.
     */
    private participationId?: number;

    constructor(page: Page) {
        this.page = page;
    }

    /** Remembers the participation id embedded in a repository request URL, e.g. `.../participations/42/repository/file`. */
    private rememberParticipationId(url: string) {
        const match = /\/participations\/(\d+)\/repository\//.exec(url);
        if (match) {
            this.participationId = Number(match[1]);
        }
    }

    findFileBrowser(exerciseID: number) {
        return getExercise(this.page, exerciseID).locator('[data-testid="cardFiles"]');
    }

    async typeSubmission(exerciseID: number, submission: ProgrammingExerciseSubmission): Promise<WrittenFile[]> {
        const written: WrittenFile[] = [];
        for (const newFile of submission.files) {
            let repositoryPath: string;
            if (submission.createFilesInRootFolder) {
                repositoryPath = await this.createFileInRootFolder(exerciseID, newFile.name);
            } else {
                repositoryPath = await this.createFileInRootPackage(exerciseID, newFile.name, submission.packageName!);
            }
            const fileContent = await Fixtures.get(newFile.path);
            // Set the file content through the Monaco API (with a verify-and-retry + sustained-value
            // check) rather than dispatching a synthetic ClipboardEvent paste. Monaco's paste handler
            // lives on a hidden textarea, so a synthetic paste on `.view-lines` is occasionally dropped
            // — most often when the JS main thread is saturated under heavy multi-node load. A dropped
            // paste committed an empty file on submit, producing a spurious 0% build result (the
            // "Re-renders the sidebar card" test failed exactly this way). setMonacoEditorContentByLocator
            // confirms Monaco actually holds the content before we submit.
            const editorContainer = getExercise(this.page, exerciseID).locator('jhi-code-editor-monaco');
            await setMonacoEditorContentByLocator(this.page, editorContainer, fileContent!);
            written.push({ repositoryPath, content: fileContent! });
        }
        await this.page.waitForTimeout(500);
        return written;
    }

    async deleteFile(exerciseID: number, name: string) {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/programming/participations/*/repository/**`);
        await this.findFile(exerciseID, name).locator('[data-testid="file-browser-file-delete"]').click();
        await this.page.locator('[data-testid="delete-file"]').click();
        const response = await responsePromise;
        expect(response.status()).toBe(200);
        await expect(this.findFile(exerciseID, name)).not.toBeVisible();
    }

    private findFile(exerciseID: number, name: string) {
        return this.findFileBrowser(exerciseID).locator('[data-testid="file-browser-file"]', { hasText: name });
    }

    async openFileWithName(exerciseID: number, name: string) {
        await this.findFile(exerciseID, name).click();
        await this.page.waitForTimeout(500);
    }

    /**
     * Clicks submit. By default also waits for the build result score to appear (the standard non-exam flow).
     * Pass `waitForResult = false` for exam setup, where the score-producing build only runs after the due
     * date and is verified separately — waiting here would block on a result that is not (yet) produced and
     * could overrun the enclosing hook/exam window. In that case we still wait for the commit to be persisted
     * server-side (the `#submit_button` click commits via POST .../repository/commit), so the caller can hand
     * in the exam without risking that the commit lands too late and an empty repository gets built.
     */
    async submit(exerciseID: number, waitForResult = true) {
        const submitButton = this.page.locator('#submit-exercise, [data-testid="submit-exercise-popover"], #submit_button').first();
        if (waitForResult) {
            await submitButton.click();
            await expect(this.page.locator('#exercise-header #result-score, jhi-code-editor-container #result-score').first()).toBeVisible({ timeout: 200000 });
            return;
        }
        // Wait for the commit request triggered by the submit click to complete before returning. Tolerant of
        // a missing response (e.g. nothing to commit) so it never hangs — it degrades to proceeding immediately.
        const commitResponse = this.page
            .waitForResponse((response) => /\/programming\/participations\/\d+\/repository\/commit$/.test(response.url()) && response.request().method() === 'POST', {
                timeout: 30000,
            })
            .catch(() => undefined);
        await submitButton.click();
        await commitResponse;
    }

    async submitPractice(exerciseID: number) {
        await this.page.locator('#submit-exercise, [data-testid="submit-exercise-popover"], #submit_button').first().click();
        await expect(this.page.locator('#exercise-header #result-score, jhi-code-editor-container #result-score').first()).toBeVisible({ timeout: 200000 });
    }

    async createFileInRootFolder(exerciseID: number, fileName: string): Promise<string> {
        await getExercise(this.page, exerciseID).locator('[id="create_file_root"]').click();
        await this.page.waitForTimeout(500);
        const responsePromise = this.page.waitForResponse(`${BASE_API}/programming/participations/*/repository/file?file=${fileName}`);
        await getExercise(this.page, exerciseID).locator('[data-testid="file-browser-create-node"]').pressSequentially(fileName);
        await this.page.waitForTimeout(500);
        await getExercise(this.page, exerciseID).locator('[data-testid="file-browser-create-node"]').press('Enter');
        const response = await responsePromise;
        expect(response.status()).toBe(200);
        this.rememberParticipationId(response.url());
        await expect(this.findFileBrowser(exerciseID).filter({ hasText: fileName })).toBeVisible();
        await this.page.waitForTimeout(500);
        return fileName;
    }

    async createFileInRootPackage(exerciseID: number, fileName: string, packageName: string): Promise<string> {
        const packagePath = packageName.replace(/\./g, '/');
        const filePath = `src/${packagePath}/${fileName}`;
        await getExercise(this.page, exerciseID).locator('[data-testid="file-browser-folder-create-file"]').nth(2).click();
        await this.page.waitForTimeout(500);
        const responsePromise = this.page.waitForResponse(`${BASE_API}/programming/participations/*/repository/file?file=${filePath}`);
        await getExercise(this.page, exerciseID).locator('[data-testid="file-browser-create-node"]').pressSequentially(fileName);
        await this.page.waitForTimeout(500);
        await getExercise(this.page, exerciseID).locator('[data-testid="file-browser-create-node"]').press('Enter');
        const response = await responsePromise;
        expect(response.status()).toBe(200);
        this.rememberParticipationId(response.url());
        await expect(this.findFileBrowser(exerciseID).filter({ hasText: fileName })).toBeVisible();
        await this.page.waitForTimeout(500);
        return filePath;
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
        return this.page.locator('[data-testid="cardBuildOutput"]');
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
        const written = await this.typeSubmission(exerciseID, submission);
        await this.awaitRepositoryContentBeforeSubmit(written);
        await this.submit(exerciseID);
        await verifyOutput();
    }

    /**
     * Reads the submitted files back out of the participation's repository and compares them to what was typed.
     * <p>
     * Waited for rather than checked afterwards, because submitting commits what the server holds, not what the
     * browser shows. A commit that overtakes the editor's save captures the previous content, and the build then runs
     * against the template code: it produces a real result with real failing tests, so the caller's score assertion
     * reports 0% and reads as a grading or product bug while the submission simply never arrived. Reading the file
     * back after the commit does not catch that at all, since the save has landed by then.
     * <p>
     * The fixture content is known exactly, so a mismatch is unambiguous. It needs the participation id, which is
     * captured from the editor's own file-creation request, and skips when that was not observed (a submission flow
     * that creates no file). It must diagnose failures, never invent them.
     */
    private async awaitRepositoryContentBeforeSubmit(written: WrittenFile[]) {
        if (this.participationId === undefined || written.length === 0) {
            return;
        }
        for (const file of written) {
            const url = `${BASE_API}/programming/participations/${this.participationId}/repository/file?file=${encodeURIComponent(file.repositoryPath)}`;
            const expected = normalizeSource(file.content);
            const readBack = async () => {
                const response = await this.page.request.get(url).catch(() => undefined);
                return response?.ok() ? normalizeSource(await response.text()) : undefined;
            };
            await expect
                .poll(readBack, {
                    timeout: 30000,
                    message:
                        `The editor's changes to ${file.repositoryPath} never reached the repository. The build would run against the wrong ` +
                        `content and score 0% — this is lost editor content, not a grading failure.`,
                })
                .toBe(expected);
        }
    }
}

/** A file written into the editor during a submission, and the content it was given. */
interface WrittenFile {
    repositoryPath: string;
    content: string;
}

/**
 * Normalizes source before comparison: line endings differ between the fixture on disk and what Monaco hands back,
 * and a trailing newline is not a content loss. Anything beyond that is a real difference.
 */
function normalizeSource(source: string): string {
    return source.replace(/\r\n/g, '\n').trimEnd();
}

/**
 * A class which encapsulates a programming exercise submission taken from the k6 resources.
 *
 * @param files An array of containers, which contain the file path of the changed file as well as its name.
 */
export interface ProgrammingExerciseSubmission {
    deleteFiles: string[];
    createFilesInRootFolder: boolean;
    files: ProgrammingExerciseFile[];
    expectedResult: string;
    packageName?: string;
}

interface ProgrammingExerciseFile {
    name: string;
    path: string;
}
