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
        // The repository view runs on a new page created via `context.newPage()` in
        // `openRepositoryOnNewPage`, which bypasses the page fixture's render-check AND the
        // worker-scoped chunk pre-warm (both attached to a different context). If Angular's
        // lazy chunk fails to bootstrap on this fresh tab the "Open Commit History" link
        // never attaches and the click auto-wait consumes the entire test budget. Capture
        // the repository URL and re-navigate to it if the page drifts to /courses (which is
        // Angular's catch-all fallback when route resolution fails).
        const repositoryUrl = this.page.url();
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
                    await this.page.goto(repositoryUrl);
                } else {
                    await this.page.reload();
                }
                await this.page.waitForLoadState('load');
            }
        }
        await link.click();
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
