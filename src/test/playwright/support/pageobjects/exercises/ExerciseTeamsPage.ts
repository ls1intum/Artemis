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
     * Sets the team owner/tutor.
     * @param username - the tutor username.
     */
    async setTeamTutor(username: string) {
        const tutorSearchInput = this.page.locator('#owner-search-input');
        const listbox = this.page.getByRole('listbox');
        // Retry up to 5 times - search autocomplete can be slow under parallel load
        for (let attempt = 0; attempt < 5; attempt++) {
            await tutorSearchInput.clear();
            await this.page.waitForTimeout(300);
            await tutorSearchInput.pressSequentially(username, { delay: 50 });
            try {
                await listbox.waitFor({ state: 'visible', timeout: 10000 });
                // Wait for the specific option to appear
                const option = listbox.getByText(new RegExp(username, 'i')).first();
                await option.waitFor({ state: 'visible', timeout: 5000 });
                await option.click();
                return;
            } catch {
                if (attempt === 4) throw new Error(`Tutor search autocomplete did not appear after 5 attempts for '${username}'`);
            }
        }
    }

    /**
     * Adds a student to the team.
     * @param username - the student username.
     */
    async addStudentToTeam(username: string) {
        const studentSearchInput = this.page.locator('#student-search-input');
        await studentSearchInput.fill(username);
        const listbox = this.page.getByRole('listbox');
        await listbox.waitFor({ state: 'visible' });
        const option = listbox.getByText(new RegExp(username, 'i')).first();
        await option.waitFor({ state: 'visible', timeout: 5000 });
        await option.click();
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
