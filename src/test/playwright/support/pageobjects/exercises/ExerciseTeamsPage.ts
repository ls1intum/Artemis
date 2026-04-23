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
     * Searches for a tutor via the owner typeahead.
     *
     * The tutor typeahead (team-owner-search) uses switchMap WITHOUT debounce.
     * Every keystroke cancels the previous in-flight HTTP. Under heavy parallel
     * test load, the server can take 10-30s to respond, so the final HTTP from
     * the last keystroke may not complete within the test timeout.
     *
     * Solution: pre-fetch the tutor list via Playwright's request API (a single
     * HTTP call not subject to switchMap cancellation), then install a page.route()
     * intercept that serves the cached response instantly to all typeahead requests.
     * This makes the typeahead popup appear immediately after typing.
     */
    private async searchTutor(inputLocator: ReturnType<Page['locator']>, username: string) {
        const listbox = this.page.getByRole('listbox');

        // Pre-fetch tutor data and install route intercept for instant responses
        const courseIdMatch = this.page.url().match(/\/course-management\/(\d+)/);
        const courseId = courseIdMatch?.[1];
        let routeInstalled = false;

        if (courseId) {
            try {
                const apiResponse = await this.page.request.get(`api/core/courses/${courseId}/tutors`);
                if (apiResponse.ok()) {
                    const body = await apiResponse.body();
                    const routePattern = `**/api/core/courses/${courseId}/tutors`;
                    await this.page.route(routePattern, (route) => route.fulfill({ status: 200, contentType: 'application/json', body }));
                    routeInstalled = true;
                }
            } catch {
                // Pre-fetch failed; fall through to normal typeahead behavior
            }
        }

        try {
            // Retry with different input strategies — fill() can fail to trigger
            // Angular's typeahead if change detection doesn't pick up the event.
            for (let attempt = 0; attempt < 3; attempt++) {
                if (attempt > 0) {
                    await this.page.waitForTimeout(500);
                    await inputLocator.clear();
                }
                // First attempt: fill() (fast). Subsequent: pressSequentially (reliable).
                if (attempt === 0) {
                    await inputLocator.fill(username);
                } else {
                    await inputLocator.click();
                    await inputLocator.pressSequentially(username, { delay: 50 });
                }
                try {
                    await listbox.waitFor({ state: 'visible', timeout: 10000 });
                    const option = listbox.getByText(new RegExp(username, 'i')).first();
                    await option.waitFor({ state: 'visible', timeout: 5000 });
                    await option.click();
                    break;
                } catch {
                    if (attempt === 2) throw new Error(`Tutor search autocomplete did not appear after 3 attempts for '${username}'`);
                }
            }
        } finally {
            if (routeInstalled && courseId) {
                await this.page.unroute(`**/api/core/courses/${courseId}/tutors`);
            }
        }
    }

    /**
     * Searches for a student via the student typeahead.
     * The student typeahead uses debounceTime(200) which coalesces keystrokes
     * into a single HTTP request after typing stops, so pressSequentially is safe.
     */
    private async searchStudent(inputLocator: ReturnType<Page['locator']>, username: string) {
        const listbox = this.page.getByRole('listbox');
        for (let attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                await this.page.waitForTimeout(500);
            }

            await inputLocator.click();
            await inputLocator.fill('');
            await this.page.waitForTimeout(300);
            await inputLocator.pressSequentially(username, { delay: 100 });

            try {
                await listbox.waitFor({ state: 'visible', timeout: 10000 });
                const option = listbox.getByText(new RegExp(username, 'i')).first();
                await option.waitFor({ state: 'visible', timeout: 5000 });
                await option.click();
                return;
            } catch {
                if (attempt === 2) throw new Error(`Student search autocomplete did not appear after 3 attempts for '${username}'`);
            }
        }
    }

    /**
     * Sets the team owner/tutor.
     * @param username - the tutor username.
     */
    async setTeamTutor(username: string) {
        await this.searchTutor(this.page.locator('#owner-search-input'), username);
    }

    /**
     * Adds a student to the team.
     * @param username - the student username.
     */
    async addStudentToTeam(username: string) {
        await this.searchStudent(this.page.locator('#student-search-input'), username);
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
