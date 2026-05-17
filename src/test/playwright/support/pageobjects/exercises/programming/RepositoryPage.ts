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
        // The repository view is opened on a new page created via `context.newPage()` in
        // `openRepositoryOnNewPage`, bypassing the `page` fixture's Angular-render check.
        // Under heavy parallel multi-node load that fresh page occasionally fails to fully
        // bootstrap and the "Open Commit History" link never attaches. Use
        // `reloadUntilFound` so a stalled chunk fetch recovers via a single reload instead
        // of consuming the entire 540 s slow-test timeout.
        const link = this.page.locator('a', { hasText: 'Open Commit History' });
        await Commands.reloadUntilFound(this.page, link, 15_000, 60_000);
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
