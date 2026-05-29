import { Page } from 'playwright';
import { expect } from '@playwright/test';

/** Escapes regex meta-characters in `value` so it can be used as a literal pattern. */
function escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Page object for the exercise teams page.
 */
export class ExerciseTeamsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Clicks the "Create team" button and waits for the dialog to be fully rendered, with
     * both typeahead inputs visible. The owner-search typeahead now uses debounceTime(200)
     * on its text$ stream (matching team-student-search), so race-prone first-keystroke
     * timing is handled at the component layer instead of by waiting on a host-property
     * attribute here.
     */
    async createTeam() {
        await this.page.locator('button', { hasText: 'Create team' }).click();
        await this.page.getByRole('dialog').waitFor({ state: 'visible', timeout: 30_000 });
        await this.page.locator('#owner-search-input').waitFor({ state: 'visible', timeout: 30_000 });
        await this.page.locator('#student-search-input').waitFor({ state: 'visible', timeout: 30_000 });
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
     *
     * The team-update-dialog runs an asynchronous short-name uniqueness check on every
     * change with a 500ms debounce: `shortName$.pipe(debounceTime(500), switchMap(... existsByShortName ...))`.
     * If we move on to interact with the tutor typeahead before that HTTP finishes, the
     * resulting change-detection cycle on the response sometimes interferes with the
     * ngbTypeahead directive's first input event. Wait through the debounce window so
     * the validation request fires + settles before subsequent UI interactions.
     *
     * @param teamShortName - the team short name.
     */
    async enterTeamShortName(teamShortName: string) {
        await this.page.locator('#teamShortName').fill(teamShortName);
        // Debounce is 500 ms; allow another 400 ms for the existsByShortName response.
        await this.page.waitForTimeout(900);
    }

    /**
     * Searches for a tutor via the owner typeahead.
     *
     * The team-owner-search typeahead now applies `debounceTime(200) + distinctUntilChanged`
     * on its text$ stream (matching team-student-search), so rapid typing coalesces into a
     * single trailing HTTP rather than cascading through switchMap cancellations. We still
     * pre-fetch the tutor list via Playwright's request API and install a `page.route`
     * intercept so the typeahead's HTTP is fulfilled instantly regardless of server load —
     * under heavy parallel multi-node load the real `GET /api/course/courses/{id}/tutors`
     * round-trip can occasionally exceed the listbox wait timeout even with debounce.
     *
     * We deliberately serve the REAL server response (rather than a synthetic one) so the
     * typeahead's selected `User` object carries every field the server later cross-checks
     * during the team save — synthetic payloads can subtly differ from the real entity
     * (extra fields, missing metadata) and break the save path.
     *
     * If every pre-fetch retry fails, we fall through to the real (slower) network — the
     * fallback timeout is large enough to absorb a few server hiccups.
     */
    private async searchTutor(inputLocator: ReturnType<Page['locator']>, username: string) {
        const listbox = this.page.getByRole('listbox');

        const courseIdMatch = this.page.url().match(/\/course-management\/(\d+)/);
        const courseId = courseIdMatch?.[1];
        const tutorsUrlSubstring = courseId ? `/api/course/courses/${courseId}/tutors` : undefined;
        const routePattern = courseId ? `**/api/course/courses/${courseId}/tutors` : undefined;
        let routeInstalled = false;

        if (routePattern && courseId) {
            const cachedBody = await this.fetchTutorListWithRetries(courseId, username);
            if (cachedBody) {
                // Convert Buffer → string explicitly: some Playwright versions handle Buffer bodies
                // inconsistently when the response is JSON, occasionally surfacing as
                // HttpErrorResponse on the Angular side. Passing a UTF-8 string is unambiguous.
                const bodyText = cachedBody.toString('utf-8');
                await this.page.route(routePattern, (route) =>
                    route.fulfill({
                        status: 200,
                        headers: { 'content-type': 'application/json' },
                        body: bodyText,
                    }),
                );
                routeInstalled = true;
            }
        }

        try {
            await inputLocator.waitFor({ state: 'visible', timeout: 30_000 });

            // Settle for any pending change detection from the previous fields (most
            // importantly the team-shortname `existsByShortName` 500 ms-debounced HTTP)
            // before touching the tutor typeahead. Under heavy parallel multi-node load the
            // ngbTypeahead directive's `_valueChanges$` listener occasionally needs a tick
            // to fully wire up to the host input — a short settle reliably moves the
            // typeahead's setup out of the contended window where the very first interaction
            // would otherwise be missed.
            await this.page.waitForTimeout(800);

            // The listbox timeout per attempt is large enough to absorb real-network latency
            // when the route mock is not installed (prefetch failed), but short enough that
            // four retries still fit comfortably inside the per-test budget.
            const listboxTimeoutMs = routeInstalled ? 15_000 : 45_000;
            for (let attempt = 0; attempt < 4; attempt++) {
                const tutorResponsePromise = tutorsUrlSubstring
                    ? this.page.waitForResponse((resp) => resp.url().includes(tutorsUrlSubstring) && resp.ok(), { timeout: listboxTimeoutMs }).catch(() => undefined)
                    : Promise.resolve(undefined);

                // pressSequentially against the locator: auto-focuses the input, fires real
                // keyboard input events that ngbTypeahead's `fromEvent(_, 'input')` listener
                // reliably receives — matching the proven `searchStudent` pattern. After
                // typing we wait briefly to honour the typeahead's `debounceTime(200)` on
                // text$ so the final value lands as a single trailing emission rather than
                // a burst that switchMap's still-resolving inner HTTP cancels.
                await inputLocator.click();
                await inputLocator.fill('');
                await this.page.waitForTimeout(300);
                await inputLocator.pressSequentially(username, { delay: 100 });

                // Wait for the tutors HTTP to complete (mock or real). The route mock returns
                // instantly; the real network can take a few seconds under load. If the wait
                // times out we still proceed to the visibility check — the typeahead may have
                // emitted from a previously-cached list without re-issuing the HTTP.
                await tutorResponsePromise;

                try {
                    await listbox.waitFor({ state: 'visible', timeout: listboxTimeoutMs });
                    const option = listbox.getByText(new RegExp(escapeRegExp(username), 'i')).first();
                    await option.waitFor({ state: 'visible', timeout: 5_000 });
                    await option.click();
                    return;
                } catch {
                    if (attempt === 3) throw new Error(`Tutor search autocomplete did not appear after 4 attempts for '${username}'`);
                    // On retry: re-trigger the typeahead by dispatching a synthetic input
                    // event with the value (in addition to the natural pressSequentially
                    // chain above). Under heavy multi-node load the first natural input
                    // event from a brand-new ngbTypeahead host occasionally reaches the
                    // listener before the directive has subscribed to its result observable;
                    // a follow-up dispatch lands after the subscription is established.
                    await inputLocator.evaluate((el: HTMLInputElement, value: string) => {
                        el.value = value;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                    }, username);
                    await this.page.waitForTimeout(500);
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
        const url = `api/course/courses/${courseId}/tutors`;
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
                const option = listbox.getByText(new RegExp(escapeRegExp(username), 'i')).first();
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
