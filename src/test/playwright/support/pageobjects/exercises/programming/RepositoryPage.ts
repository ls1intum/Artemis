import { Page, expect } from '@playwright/test';
import { ExerciseCommit } from '../../../constants';
import { Commands } from '../../../commands';

export class RepositoryPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openCommitHistory() {
        await this.page.locator('a', { hasText: 'Open Commit History' }).click();
    }

    async checkCommitHistory(commits: ExerciseCommit[]) {
        // Reverse commit history as the latest commit in commit history table is at the bottom
        commits = commits.reverse();
        const commitHistory = this.page.locator('.card-body', { hasText: 'Commit History' });

        if (commits) {
            const commitCount = commits.length;
            for (let index = commitCount - 1; index > 0; index--) {
                const commit = commits[index];
                const commitRow = commitHistory.locator('tbody').locator('tr').nth(index);
                await expect(commitRow.locator('td').getByText(commit.message)).toBeVisible();

                if (commit.result) {
                    const commitResult = commitRow.locator('#result-score', { hasText: commit.result });
                    await Commands.reloadUntilFound(this.page, commitResult, 2000, 60000);
                } else {
                    await expect(commitRow.locator('td', { hasText: 'No result' })).toBeVisible();
                }
            }
        }
    }
}
