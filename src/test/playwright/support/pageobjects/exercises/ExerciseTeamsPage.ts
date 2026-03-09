import { Page } from 'playwright';
import { expect } from '@playwright/test';

/**
 * Page object for the exercise teams page.
 */
export class ExerciseTeamsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Clicks the "Create team" button.
     */
    async createTeam() {
        await this.page.locator('button', { hasText: 'Create team' }).click();
    }

    /**
     * Enters the team name.
     * @param teamName - the team name.
     */
    async enterTeamName(teamName: string) {
        await this.page.locator('#teamName').fill(teamName);
    }

    /**
     * Enters the team short name.
     * @param teamShortName - the team short name.
     */
    async enterTeamShortName(teamShortName: string) {
        await this.page.locator('#teamShortName').fill(teamShortName);
    }

    /**
     * Searches for a user via an autocomplete input and selects the matching option.
     * Retries up to 3 times to handle autocomplete timing issues (debounce, API latency).
     */
    private async searchAndSelect(inputLocator: ReturnType<Page['locator']>, username: string, role: string) {
        const listbox = this.page.getByRole('listbox');
        for (let attempt = 0; attempt < 3; attempt++) {
            await inputLocator.clear();
            await inputLocator.fill(username);
            try {
                await listbox.waitFor({ state: 'visible', timeout: 8000 });
                const option = listbox.getByText(new RegExp(username, 'i')).first();
                await option.waitFor({ state: 'visible', timeout: 5000 });
                await option.click();
                return;
            } catch {
                if (attempt === 2) throw new Error(`${role} search autocomplete did not appear after 3 attempts for '${username}'`);
            }
        }
    }

    /**
     * Sets the team owner/tutor.
     * @param username - the tutor username.
     */
    async setTeamTutor(username: string) {
        await this.searchAndSelect(this.page.locator('#owner-search-input'), username, 'Tutor');
    }

    /**
     * Adds a student to the team.
     * @param username - the student username.
     */
    async addStudentToTeam(username: string) {
        await this.searchAndSelect(this.page.locator('#student-search-input'), username, 'Student');
    }

    /**
     * Checks if the team is on the list of teams.
     * @param teamShortName - the team short name.
     */
    async checkTeamOnList(teamShortName: string) {
        await expect(this.page.getByRole('table').getByRole('row').getByText(teamShortName)).toBeVisible();
    }

    /**
     * Retrieves the Locator to ignore team size recommendation checkbox.
     */
    getIgnoreTeamSizeRecommendationCheckbox() {
        return this.page.locator('#ignoreTeamSizeRecommendation');
    }

    /**
     * Retrieves the Locator for the save button.
     */
    getSaveButton() {
        return this.page.locator('button', { hasText: 'Save' });
    }
}
