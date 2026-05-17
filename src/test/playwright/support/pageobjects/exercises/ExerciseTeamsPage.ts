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
     * Clicks the "Create team" button and waits for the dialog to be fully rendered
     * (heading + both typeahead inputs attached) before returning. Without this wait,
     * a subsequent `enterTeamName` / `setTeamTutor` can race the dialog's component
     * tree mount and the `NgbTypeahead` directive subscription is not yet wired up
     * when keystrokes start flowing.
     */
    async createTeam() {
        await this.page.locator('button', { hasText: 'Create team' }).click();
        await this.page.getByRole('dialog').waitFor({ state: 'visible', timeout: 30_000 });
        await this.page.locator('#owner-search-input').waitFor({ state: 'attached', timeout: 30_000 });
        await this.page.locator('#student-search-input').waitFor({ state: 'attached', timeout: 30_000 });
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
     * The tutor typeahead (team-owner-search) uses switchMap WITHOUT debounce, so every
     * keystroke fires a new HTTP request and cancels the previous one. Under heavy parallel
     * multi-node load the GET /api/core/courses/{id}/tutors round-trip can take 10-30s and
     * the final response from the LAST keystroke may not arrive within the test budget.
     *
     * Strategy: pre-fetch the real tutor list via Playwright's request API (one HTTP call
     * not subject to switchMap cancellation), with retries to ride out transient slowness.
     * Then install a `page.route` intercept that serves the cached body instantly to every
     * subsequent typeahead request. We deliberately use the REAL server response (rather
     * than a synthetic one) so the typeahead's selected `User` object carries every field
     * the server later cross-checks during the team save — synthetic payloads can subtly
     * differ from the real entity (extra fields, missing metadata) and break the save path.
     *
     * If every pre-fetch retry fails, we fall through to the real (slower) network — the
     * fallback timeout is large enough to absorb a few server hiccups.
     */
    private async searchTutor(inputLocator: ReturnType<Page['locator']>, username: string) {
        const listbox = this.page.getByRole('listbox');

        const courseIdMatch = this.page.url().match(/\/course-management\/(\d+)/);
        const courseId = courseIdMatch?.[1];
        const routePattern = courseId ? `**/api/core/courses/${courseId}/tutors` : undefined;
        let routeInstalled = false;

        if (routePattern && courseId) {
            const cachedBody = await this.fetchTutorListWithRetries(courseId, username);
            if (cachedBody) {
                await this.page.route(routePattern, (route) => route.fulfill({ status: 200, contentType: 'application/json', body: cachedBody }));
                routeInstalled = true;
            }
        }

        try {
            await inputLocator.waitFor({ state: 'visible', timeout: 30_000 });

            // The listbox timeout per attempt is large enough to absorb real-network latency
            // when the route mock is not installed (prefetch failed), but short enough that
            // four retries still fit comfortably inside the per-test budget.
            const listboxTimeoutMs = routeInstalled ? 15_000 : 45_000;
            for (let attempt = 0; attempt < 4; attempt++) {
                if (attempt > 0) {
                    await this.page.waitForTimeout(500);
                    await inputLocator.clear();
                }
                // Focus the input via a click; the ngbTypeahead directive listens to focus
                // and click events on the host element, so this also primes its internal
                // subject before keystrokes start flowing.
                await inputLocator.click();
                // Use real keyboard events via Page.keyboard.type — `fill()` sometimes does
                // not interact with ngbTypeahead's internal ValueAccessor correctly when the
                // ngModel is bound to a non-string (User) value. With debounceTime(200) on
                // text$, the keystrokes coalesce into a single HTTP after the typing window.
                await this.page.keyboard.type(username, { delay: 30 });
                try {
                    await listbox.waitFor({ state: 'visible', timeout: listboxTimeoutMs });
                    const option = listbox.getByText(new RegExp(username, 'i')).first();
                    await option.waitFor({ state: 'visible', timeout: 5_000 });
                    await option.click();
                    return;
                } catch {
                    if (attempt === 3) throw new Error(`Tutor search autocomplete did not appear after 4 attempts for '${username}'`);
                }
            }
        } finally {
            if (routeInstalled && routePattern) {
                await this.page.unroute(routePattern);
            }
        }
    }

    /**
     * Pre-fetches the course tutor list with retries. Uses Playwright's request context
     * (a single HTTP call not subject to switchMap cancellation) so the route intercept
     * can serve subsequent typeahead requests deterministically.
     *
     * Treats both empty responses AND responses missing the expected user as transient
     * failures and retries. Under heavy parallel load the server occasionally returns
     * a list that omits the seeded tutor (a node whose user-group / Hibernate session
     * has not yet picked up the membership). Returning that payload via the route mock
     * would make the typeahead silently filter to zero results — exactly the failure
     * mode that has been chasing us across runs.
     *
     * Returns `undefined` if every retry fails — the caller then falls back to the
     * real (slower) network path with a more generous per-attempt timeout.
     */
    private async fetchTutorListWithRetries(courseId: string, expectedUsername: string): Promise<Buffer | undefined> {
        const url = `api/core/courses/${courseId}/tutors`;
        const maxAttempts = 8;
        const perAttemptTimeoutMs = 15_000;
        const backoffMs = 1_000;
        for (let attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                const response = await this.page.request.get(url, { timeout: perAttemptTimeoutMs });
                if (response.ok()) {
                    const body = await response.body();
                    if (this.bodyContainsUser(body, expectedUsername)) {
                        return body;
                    }
                }
            } catch {
                // network/timeout — fall through to backoff + retry
            }
            await this.page.waitForTimeout(backoffMs);
        }
        return undefined;
    }

    /**
     * Checks whether the JSON body parses as an array of users containing one whose
     * `login` matches the expected username (case-insensitive substring match — the
     * typeahead's own filter uses the same logic, so this mirrors what would actually
     * show up in the listbox after filtering).
     */
    private bodyContainsUser(body: Buffer, expectedUsername: string): boolean {
        try {
            const parsed = JSON.parse(body.toString('utf-8'));
            if (!Array.isArray(parsed) || parsed.length === 0) {
                return false;
            }
            const needle = expectedUsername.toLowerCase();
            return parsed.some((u: { login?: string; name?: string }) => {
                return (u.login ?? '').toLowerCase().includes(needle) || (u.name ?? '').toLowerCase().includes(needle);
            });
        } catch {
            return false;
        }
    }

    /**
     * Searches for a student via the student typeahead.
     * The student typeahead uses debounceTime(200) which coalesces keystrokes
     * into a single HTTP request after typing stops, so pressSequentially is safe.
     */
    private async searchStudent(inputLocator: ReturnType<Page['locator']>, username: string) {
        const listbox = this.page.getByRole('listbox');
        // Ensure the input is mounted before we start typing — under parallel CI load the dialog
        // body can render late and pressSequentially against an absent element silently no-ops.
        await inputLocator.waitFor({ state: 'visible', timeout: 30_000 });
        for (let attempt = 0; attempt < 4; attempt++) {
            if (attempt > 0) {
                await this.page.waitForTimeout(500);
            }

            await inputLocator.click();
            await inputLocator.fill('');
            await this.page.waitForTimeout(300);
            await inputLocator.pressSequentially(username, { delay: 100 });

            try {
                await listbox.waitFor({ state: 'visible', timeout: 15000 });
                const option = listbox.getByText(new RegExp(username, 'i')).first();
                await option.waitFor({ state: 'visible', timeout: 5000 });
                await option.click();
                return;
            } catch {
                if (attempt === 3) throw new Error(`Student search autocomplete did not appear after 4 attempts for '${username}'`);
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
