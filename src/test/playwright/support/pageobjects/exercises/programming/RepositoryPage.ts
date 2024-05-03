import { Page, expect } from '@playwright/test';
import { ExerciseCommit } from '../../../constants';

export class RepositoryPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openCommitHistory() {
        await this.page.locator('a', { hasText: 'Open Commit History' }).click();
    }

    async checkCommitHistory(commits: ExerciseCommit[]) {
        const commitHistory = this.page.locator('.card-body', { hasText: 'Commit History' });

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
