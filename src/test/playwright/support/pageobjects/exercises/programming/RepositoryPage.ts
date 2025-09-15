import { Page, expect } from '@playwright/test';
import { ExerciseCommit } from '../../../constants';
import { Commands } from '../../../commands';

export class RepositoryPage {
    private readonly page: Page;

    private static readonly CHECK_BUILD_RESULT_INTERVAL = 2000;
    private static readonly CHECK_BUILD_RESULT_TIMEOUT = 90000;

    constructor(page: Page) {
        this.page = page;
    }

    async openCommitHistory() {
        await this.page.locator('a', { hasText: 'Open Commit History' }).click();
    }

    async checkCommitHistory(commits: ExerciseCommit[]) {
        const commitHistory = this.page.locator('#course-body-container', { hasText: 'Commit History' });

        if (commits) {
            // Initial commit is at the bottom of the table
            const initialCommitIndexInTable = commits.length;
            for (let index = 0; index < commits.length; index++) {
                const commit = commits[index];
                const commitIndexInTable = initialCommitIndexInTable - 1 - index;
                const commitRow = commitHistory.locator('tbody').locator('tr').nth(commitIndexInTable);
                await expect(commitRow.locator('td').getByText(commit.message)).toBeVisible();

                if (commit.result) {
                    const commitResult = commitRow.locator('#result-score', { hasText: commit.result });
                    await Commands.reloadUntilFound(this.page, commitResult, RepositoryPage.CHECK_BUILD_RESULT_INTERVAL, RepositoryPage.CHECK_BUILD_RESULT_TIMEOUT);
                } else {
                    await expect(commitRow.locator('td', { hasText: 'No result' })).toBeVisible();
                }
            }
        }
    }
}
