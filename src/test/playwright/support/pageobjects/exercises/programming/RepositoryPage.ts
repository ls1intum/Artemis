import { Page, expect } from '@playwright/test';
import { ExerciseCommit } from '../../../constants';
import { Commands } from '../../../commands';
import { BUILD_RESULT_TIMEOUT, POLLING_INTERVAL } from '../../../timeouts';

export class RepositoryPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openCommitHistory() {
        const repositoryUrl = this.page.url();
        console.log(`[RepositoryPage] Current URL before opening commit history: ${repositoryUrl}`);
        // The repository view is opened on a new page created via `context.newPage()` in
        // `openRepositoryOnNewPage`, bypassing the `page` fixture's Angular-render check.
        // Under heavy parallel multi-node load that fresh page occasionally fails to fully
        // bootstrap and Angular's router falls back to /courses. A bare reload of /courses
        // never recovers, so navigate back to the repository URL on each attempt.
        const link = this.page.locator('a', { hasText: 'Open Commit History' });
        const maxAttempts = 5;
        for (let attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                await link.waitFor({ state: 'visible', timeout: 15_000 });
                break;
            } catch {
                if (attempt === maxAttempts - 1) {
                    throw new Error(`openCommitHistory: link did not appear after ${maxAttempts} attempts (last URL: ${this.page.url()}, expected: ${repositoryUrl})`);
                }
                if (this.page.url() !== repositoryUrl) {
                    console.log(`[RepositoryPage] Page drifted to ${this.page.url()}; re-navigating to ${repositoryUrl}`);
                    await this.page.goto(repositoryUrl);
                } else {
                    await this.page.reload();
                }
                await this.page.waitForLoadState('load');
            }
        }
        await link.click();
        console.log('[RepositoryPage] Clicked "Open Commit History" link');
    }

    async checkCommitHistory(commits: ExerciseCommit[]) {
        console.log(`[checkCommitHistory] Checking ${commits?.length || 0} commits`);
        console.log(`[checkCommitHistory] Current page URL: ${this.page.url()}`);

        const commitHistory = this.page.locator('#course-body-container', { hasText: 'Commit History' });

        if (commits) {
            // Initial commit is at the bottom of the table
            const initialCommitIndexInTable = commits.length;
            for (let index = 0; index < commits.length; index++) {
                const commit = commits[index];
                const commitIndexInTable = initialCommitIndexInTable - 1 - index;
                console.log(
                    `[checkCommitHistory] Checking commit ${index + 1}/${commits.length}: "${commit.message}" expecting result "${commit.result}" (row ${commitIndexInTable})`,
                );

                const commitRow = commitHistory.locator('tbody').locator('tr').nth(commitIndexInTable);
                await expect(commitRow.locator('td').getByText(commit.message)).toBeVisible();
                console.log(`[checkCommitHistory] Found commit message "${commit.message}"`);

                if (commit.result) {
                    const resultLocator = commitRow.locator('#result-score');
                    console.log(`[checkCommitHistory] Waiting for result "${commit.result}" to appear...`);
                    await Commands.reloadUntilTextFound(this.page, resultLocator, commit.result, POLLING_INTERVAL, BUILD_RESULT_TIMEOUT);

                    const actualResult = await resultLocator.textContent();
                    console.log(`[checkCommitHistory] Found result: "${actualResult}", expected: "${commit.result}"`);

                    if (!actualResult?.includes(commit.result)) {
                        throw new Error(`Commit "${commit.message}": Expected result "${commit.result}" but found "${actualResult}"`);
                    }
                    console.log(`[checkCommitHistory] Result matches expected ✓`);
                } else {
                    await expect(commitRow.locator('td', { hasText: 'No result' })).toBeVisible();
                    console.log(`[checkCommitHistory] Found "No result" as expected ✓`);
                }
            }
        }
        console.log('[checkCommitHistory] All commits verified successfully');
    }
}
