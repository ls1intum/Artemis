import { UserCredentials } from './users';
import { Locator, Page, expect } from '@playwright/test';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseAPIRequests } from './requests/ExerciseAPIRequests';
import { BUILD_FINISH_TIMEOUT, POLLING_INTERVAL } from './timeouts';

/**
 * A class that encapsulates static helper command methods.
 */
export class Commands {
    /**
     * Logs in via API authentication.
     * @param page - Playwright page object.
     * @param credentials - UserCredentials object containing username and password.
     * @param url - Optional URL to navigate to after successful login.
     */
    static login = async (page: Page, credentials: UserCredentials, url?: string): Promise<void> => {
        await Commands.logout(page);
        await page.context().clearCookies();
        const { username, password } = credentials;
        // Retry the auth POST on transient 5xx — under heavy parallel multi-node load
        // the JWT filter / Hazelcast cluster occasionally returns 503 for a few seconds
        // while a node spins up its Eureka registration or rebalances. Bailing on the
        // first attempt would surface as a flaky test failure that has nothing to do
        // with the code under test.
        const maxAttempts = 5;
        let response: Awaited<ReturnType<typeof page.request.post>> | undefined;
        for (let attempt = 0; attempt < maxAttempts; attempt++) {
            response = await page.request.post(`api/core/public/authenticate`, {
                data: {
                    username,
                    password,
                    rememberMe: true,
                },
                failOnStatusCode: false,
            });
            if (response.status() === 200) {
                break;
            }
            if (response.status() < 500 || attempt === maxAttempts - 1) {
                // 4xx is a permanent failure (bad credentials etc.) — do not retry.
                // 5xx on the final attempt also escapes the loop so the assertion
                // below surfaces the actual response.
                break;
            }
            await page.waitForTimeout(1_500 * (attempt + 1));
        }

        expect(response!.status()).toBe(200);

        // The previous user's JWT cookie has been cleared and a new one set for `username`.
        // Verify by re-reading: the cookie jar must contain exactly one jwt that is non-empty.
        // We do not look up the cookie by value (we only have the token after auth) — finding any
        // jwt cookie after clearCookies + this auth POST is sufficient.
        await expect
            .poll(
                async () =>
                    page
                        .context()
                        .cookies()
                        .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt')?.value),
                { timeout: 10000 },
            )
            .toBeTruthy();

        if (url) {
            // page.goto triggers a full document navigation, which re-bootstraps Angular and the
            // APP_INITIALIZER fetches /api/core/public/account with the freshly-set JWT cookie.
            // Even so, under heavy parallel load we have observed Angular components occasionally
            // rendering with the previous user's cached identity (the AccountService userIdentity
            // signal is initialized from APP_INITIALIZER but a stale Angular state from the prior
            // route can persist briefly). Verify the navbar shows the expected user before letting
            // the test interact with the page; if not, force a hard reload to discard any cached
            // SPA state and re-bootstrap from scratch.
            await page.goto(url);
            await page.waitForLoadState('load');
            await Commands.verifyAuthenticatedAs(page, credentials);
            // Under heavy multi-node CI load the post-goto URL has been observed to drift to
            // a bare /courses (the Angular auth/router fall-back when a lazy route chunk fails
            // to resolve). Detect that specific drift and re-issue the goto so callers actually
            // land on the target URL rather than the fall-back. We only act on the bare
            // /courses pathname — other URL transformations (e.g. trailing slashes, querystring
            // additions, redirects to legitimate sub-routes) are left alone.
            if (Commands.driftedToCoursesFallback(url, page.url())) {
                await page.goto(url);
                await page.waitForLoadState('load');
            }
        }
    };

    /**
     * Detects the specific lazy-chunk-load fallback where Angular routes the page to a bare
     * `/courses` after a navigation to a different intended URL. Returns true only when
     * the caller-requested URL was NOT itself the bare `/courses` and the current URL has
     * resolved to exactly that fall-back.
     */
    private static driftedToCoursesFallback(requestedUrl: string, currentUrl: string): boolean {
        const currentPath = new URL(currentUrl).pathname;
        const isOnCoursesFallback = /^\/courses\/?$/.test(currentPath);
        if (!isOnCoursesFallback) {
            return false;
        }
        // Allow the request to ASK for /courses without flagging it as drift.
        const requestedAbsolute = requestedUrl.startsWith('http') ? new URL(requestedUrl) : new URL(requestedUrl, currentUrl);
        return !/^\/courses\/?$/.test(requestedAbsolute.pathname);
    }

    /**
     * After page.goto, the navbar must render the just-authenticated user. Wait for
     * #account-menu to show the expected login. If a stale identity persists past the first
     * verification window, force a full page reload to rebuild Angular from scratch — this is
     * cheaper than retrying the whole login and reliably recovers from the rare race.
     * <p>
     * Routes that legitimately do not render a navbar (exam participation, problem-statement
     * standalone, LTI iframe) are detected by URL pattern and skipped — there is nothing
     * observable to verify against on those routes.
     * <p>
     * If the route SHOULD have a navbar but the navbar never attaches, the SPA's lazy-loaded
     * route module likely failed to chunk-load (a common symptom under heavy parallel load:
     * the page renders only the app shell and footer). We force one full reload to retry the
     * chunk fetch before giving up.
     */
    private static verifyAuthenticatedAs = async (page: Page, credentials: UserCredentials): Promise<void> => {
        const accountMenu = page.locator('#account-menu');
        const expectsNavbar = !Commands.isNoNavbarRoute(page.url());

        const attachedWithin = async (timeout: number): Promise<boolean> =>
            accountMenu
                .waitFor({ state: 'attached', timeout })
                .then(() => true)
                .catch(() => false);

        if (!(await attachedWithin(5_000))) {
            if (!expectsNavbar) {
                // Legitimate no-navbar route — there is nothing to verify.
                return;
            }
            // Navbar missing on a route that should have one ⇒ chunk-load failure or other
            // bootstrap glitch. Reload to retry; this typically recovers in one round-trip.
            await page.reload();
            await page.waitForLoadState('load');
            if (!(await attachedWithin(30_000))) {
                // Reload did not help — fall through so the calling test surfaces a useful
                // error against the missing target element rather than failing here.
                return;
            }
        }

        // Use a word-boundary regex rather than `toContainText(username)`. Plain substring
        // matching silently passes on the exact race this helper exists to catch: in the
        // instructor→studentOne transition the navbar still showing `artemis_test_user_16`
        // contains `artemis_test_user_1` as a prefix, so the substring assertion would pass
        // against the stale identity. `\b` after the user index (digit/underscore are word
        // chars) anchors the match to the full token.
        const escaped = credentials.username.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const expectedUser = new RegExp(`\\b${escaped}\\b`);
        const containsExpectedUser = async () => {
            try {
                await expect(accountMenu).toContainText(expectedUser, { timeout: 15000 });
                return true;
            } catch {
                return false;
            }
        };
        if (await containsExpectedUser()) {
            return;
        }
        // Mismatch — the SPA bootstrapped before the new JWT was committed to Angular's
        // AccountService cache. Hard-reload to rebuild against the now-current cookie.
        await page.reload();
        await page.waitForLoadState('load');
        await expect(accountMenu).toContainText(expectedUser, { timeout: 30000 });
    };

    /**
     * Routes whose route component intentionally suppresses the app navbar — exam
     * participation, problem-statement standalone view, LTI launch views, quiz/exercise
     * "live" or "participate" views, and exam conduction. The login verification helper
     * skips navbar checks on these routes so we do not pay a reload overhead on tests
     * targeting them.
     *
     * Note: Artemis LTI routes are mounted at `/lti/{launch|dynamic-registration|select-content|...}`
     * (`app.routes.ts` `path: 'lti'`); the literal `lti13` only appears in API endpoint
     * paths (`/api/lti/public/lti13/...`), not in router URLs.
     */
    static isNoNavbarRoute(url: string): boolean {
        return /\/exam-participation\/|\/problem-statement\/|\/lti\/(?:launch|dynamic-registration|select-content)\b|\/exercises\/[^/]+\/live\b|\/exercises\/[^/]+\/participate\b|\/exams\/\d+\/.+\/conduction/.test(
            url,
        );
    }

    static logout = async (page: Page): Promise<void> => {
        await page.request.post('api/core/public/logout');
    };

    /**
     * Navigates to a URL and waits for the Angular app to actually render the route.
     *
     * Plain `page.goto` + `waitForLoadState('domcontentloaded')` only guarantees the HTML
     * shell has parsed; under heavy parallel multi-node load the route's lazy-loaded
     * chunk occasionally fails to resolve in time, leaving the page with only the
     * app shell (banner + footer) and no navbar / route component. We detect that case
     * by waiting for the navbar's `#account-menu` (always present on routes that
     * include the navbar) and reload once if it never attaches.
     *
     * Routes that legitimately suppress the navbar (exam participation, problem-statement
     * standalone, LTI) should not use this helper — pass an explicit `renderIndicator`
     * instead, or just call `page.goto` directly.
     */
    static gotoAndEnsureRendered = async (page: Page, url: string, renderIndicator: string = '#account-menu'): Promise<void> => {
        await page.goto(url);
        await page.waitForLoadState('load');
        await Commands.ensureRendered(page, renderIndicator);
    };

    /**
     * Verifies that the Angular app has rendered the route component after a navigation
     * and reloads once if it has not. Idempotent — safe to call multiple times.
     *
     * Default behaviour (no `renderIndicator` argument): probes the navbar's
     * `#account-menu` element and skips the check entirely on routes that legitimately
     * suppress the navbar (exam-participation, problem-statement standalone, LTI, quiz
     * live, exercise participate, exam conduction) so those tests pay zero overhead.
     *
     * When the caller passes an explicit `renderIndicator` — typically a selector
     * specific to a no-navbar route — that indicator is awaited unconditionally; the
     * no-navbar skip only applies to the default navbar probe.
     */
    static ensureRendered = async (page: Page, renderIndicator: string = '#account-menu'): Promise<void> => {
        // Only skip on no-navbar routes when the caller is relying on the default navbar probe.
        if (renderIndicator === '#account-menu' && Commands.isNoNavbarRoute(page.url())) {
            return;
        }
        const indicator = page.locator(renderIndicator);
        const attachedWithin = async (timeout: number): Promise<boolean> =>
            indicator
                .waitFor({ state: 'attached', timeout })
                .then(() => true)
                .catch(() => false);
        if (await attachedWithin(5_000)) {
            return;
        }
        await page.reload();
        await page.waitForLoadState('load');
        await attachedWithin(30_000);
    };

    static reloadUntilFound = async (page: Page, locator: Locator, interval = 10000, timeout = 60000) => {
        const startTime = Date.now();

        while (Date.now() - startTime < timeout) {
            try {
                await locator.waitFor({ state: 'visible', timeout: interval });
                return;
            } catch {
                // waitFor can fail even when the element is visible (Playwright
                // timing issue with cookie propagation from page.request). Check
                // isVisible() as a fallback before reloading.
                if (await locator.isVisible()) {
                    return;
                }
                if (page.isClosed()) {
                    throw new Error(`Page was closed while waiting for element matching "${locator}"`);
                }
                try {
                    await page.reload();
                } catch (reloadError) {
                    throw new Error(`Failed to reload page while waiting for element: ${reloadError}`, { cause: reloadError });
                }
            }
        }

        throw new Error(`Timed out finding an element matching the "${locator}" locator (URL: ${page.url()})`);
    };

    static reloadUntilTextFound = async (page: Page, locator: Locator, expectedText: string | RegExp, interval = 5000, timeout = 60000) => {
        const startTime = Date.now();
        let lastSeenText: string | null = null;
        const matches = (text: string | null): boolean => text != null && (expectedText instanceof RegExp ? expectedText.test(text) : text.includes(expectedText));

        while (Date.now() - startTime < timeout) {
            try {
                await locator.waitFor({ state: 'visible', timeout: interval });
                const text = await locator.textContent();
                lastSeenText = text;
                if (matches(text)) {
                    return;
                }
            } catch {
                // Ignore and retry with a page reload below.
            }

            if (page.isClosed()) {
                throw new Error(`Page was closed while waiting for text "${expectedText}" in locator "${locator}"`);
            }

            try {
                await page.reload();
            } catch (reloadError) {
                throw new Error(`Failed to reload page while waiting for text "${expectedText}": ${reloadError}`, { cause: reloadError });
            }
        }

        throw new Error(`Timed out waiting for text "${expectedText}" in locator "${locator}" (URL: ${page.url()}). Last seen text: "${lastSeenText}"`);
    };

    /**
     * Waits for the build of an exercise to finish.
     * Throws an error if the build does not finish within the timeout.
     * @param page - Playwright page object.
     * @param exerciseAPIRequests - ExerciseAPIRequests object.
     * @param exerciseId - ID of the exercise to wait for.
     * @param interval - Interval in milliseconds between checks for the build to finish.
     * @param timeout - Timeout in milliseconds to wait for the build to finish.
     */
    static waitForExerciseBuildToFinish = async (
        page: Page,
        exerciseAPIRequests: ExerciseAPIRequests,
        exerciseId: number,
        interval: number = POLLING_INTERVAL,
        timeout: number = BUILD_FINISH_TIMEOUT,
        minResults?: number,
    ) => {
        let exerciseParticipation: StudentParticipation | undefined;
        let participationId: number | undefined;
        const startTime = Date.now();

        // Wait for a participation to become available and capture its ID once.
        while (Date.now() - startTime < timeout) {
            try {
                exerciseParticipation = await exerciseAPIRequests.getProgrammingExerciseParticipation(exerciseId);
                participationId = exerciseParticipation.id;
                break;
            } catch {
                // no participation yet — keep polling
            }
            await new Promise((resolve) => setTimeout(resolve, interval));
        }

        if (!exerciseParticipation || participationId === undefined) {
            throw new Error(`Timed out waiting for participation for exercise ${exerciseId}`);
        }

        const countResults = (participation: StudentParticipation | undefined): number => {
            return participation?.submissions ? participation.submissions.reduce((sum, submission) => sum + (submission.results?.length ?? 0), 0) : 0;
        };

        const numberOfBuildResults = countResults(exerciseParticipation);
        // If minResults is specified, wait until total results reach that count.
        // Otherwise, wait for the result count to increase by at least 1.
        const targetCount = minResults ?? numberOfBuildResults + 1;

        // Poll with a single API call per iteration now that we have the participation ID.
        while (Date.now() - startTime < timeout) {
            try {
                exerciseParticipation = await exerciseAPIRequests.getParticipationWithLatestResult(participationId);
                if (countResults(exerciseParticipation) >= targetCount) {
                    return exerciseParticipation;
                }
            } catch {
                // ignore transient errors
            }

            await new Promise((resolve) => setTimeout(resolve, interval));
        }

        throw new Error('Timed out while waiting for build to finish.');
    };

    /**
     * Waits for the build of a specific participation to finish.
     * This method uses a student-accessible endpoint (by participation ID).
     * Use this when logged in as a student who owns the participation.
     *
     * @param exerciseAPIRequests - ExerciseAPIRequests object.
     * @param participationId - ID of the participation to wait for.
     * @param interval - Interval in milliseconds between checks.
     * @param timeout - Timeout in milliseconds to wait for the build to finish.
     */
    static waitForParticipationBuildToFinish = async (
        exerciseAPIRequests: ExerciseAPIRequests,
        participationId: number,
        interval: number = POLLING_INTERVAL,
        timeout: number = BUILD_FINISH_TIMEOUT,
    ) => {
        if (participationId == null || isNaN(participationId)) {
            throw new Error(`[waitForParticipationBuildToFinish] Invalid participationId: ${participationId}. Cannot poll for build result.`);
        }
        const startTime = Date.now();

        const getLatestResultId = (participation: StudentParticipation): number | undefined => {
            const ids = (participation.submissions ?? [])
                .flatMap((s) => s.results ?? [])
                .map((r) => r.id)
                .filter((id): id is number => id !== undefined && id !== null);
            return ids.length > 0 ? Math.max(...ids) : undefined;
        };

        // Snapshot the highest result ID before the student's build starts so we can
        // detect a genuinely new result even if it arrives before the first poll.
        let initialResultId: number | undefined;
        try {
            const participation = await exerciseAPIRequests.getParticipationWithLatestResult(participationId);
            initialResultId = getLatestResultId(participation);
        } catch {
            // ignore — we will poll until we see a new result ID
        }

        while (Date.now() - startTime < timeout) {
            try {
                const participation = await exerciseAPIRequests.getParticipationWithLatestResult(participationId);
                const currentResultId = getLatestResultId(participation);

                // A new result has a different (higher) ID than the pre-build snapshot.
                // Comparing IDs rather than counts avoids the race where the build finishes
                // between makeSubmission() and the initial fetch above, leaving the count
                // permanently stuck.
                if (currentResultId !== undefined && currentResultId !== initialResultId) {
                    return participation;
                }
            } catch {
                // ignore transient poll failures — we retry until timeout
            }

            await new Promise((resolve) => setTimeout(resolve, interval));
        }

        throw new Error(`Timed out waiting for build to finish for participation ${participationId}. Initial result ID: ${initialResultId}, timeout: ${timeout}ms`);
    };

    static toggleSidebar = async (page: Page) => {
        await page.keyboard.press('Control+m');
    };
}
