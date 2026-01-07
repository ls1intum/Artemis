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
        console.log(`[RepositoryPage] Current URL before opening commit history: ${this.page.url()}`);
        await this.page.locator('a', { hasText: 'Open Commit History' }).click();
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
                    // First wait for ANY result to appear (not filtered by expected text)
                    const anyResultLocator = commitRow.locator('#result-score');
                    console.log(`[checkCommitHistory] Waiting for result-score element to appear...`);
                    await Commands.reloadUntilFound(this.page, anyResultLocator, POLLING_INTERVAL, BUILD_RESULT_TIMEOUT);

                    // Now check if the actual result matches expected
                    const actualResult = await anyResultLocator.textContent();
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
