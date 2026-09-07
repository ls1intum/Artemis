import { UserCredentials } from './users';
import { Locator, Page, errors, expect } from '@playwright/test';
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
        // Verify by re-reading: the cookie jar must contain a jwt that is non-empty. We do not look
        // up the cookie by value (we only have the token after auth) — finding any jwt cookie after
        // clearCookies + this auth POST is sufficient.
        //
        // Under heavy parallel multi-node load — and especially right after Playwright recycles a
        // crashed worker process — the Set-Cookie from the auth POST has occasionally not landed in
        // the page context's cookie jar within the poll window even though the POST returned 200.
        // Re-issue the (idempotent) auth POST and re-check rather than failing outright; the retry
        // re-sends the cookie and recovers the rare jar-propagation race. The final attempt rethrows
        // so a genuine auth failure still surfaces with the original assertion error.
        const jwtCookieValue = () =>
            page
                .context()
                .cookies()
                .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt')?.value);
        for (let cookieAttempt = 0; ; cookieAttempt++) {
            const isLastAttempt = cookieAttempt === 2;
            try {
                await expect.poll(jwtCookieValue, { timeout: isLastAttempt ? 10000 : 5000 }).toBeTruthy();
                break;
            } catch (error) {
                if (isLastAttempt) {
                    throw error;
                }
                const retryResponse = await page.request.post(`api/core/public/authenticate`, {
                    data: { username, password, rememberMe: true },
                    failOnStatusCode: false,
                });
                expect(retryResponse.status()).toBe(200);
            }
        }

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
     * Whether the page currently sits on the route that was asked for.
     * <p>
     * Only the path is compared. A reload re-bootstraps the app, and several pages rewrite their own query
     * parameters afterwards, so requiring the full URL to match would report a failed restore for a page that
     * did come back correctly. The path is what distinguishes the requested route from the `/courses`
     * fallback, from an authentication redirect, and from anywhere else the router may land.
     *
     * @param requestedUrl the url the caller asked for, absolute or relative
     * @param currentUrl   the url the page is on now
     */
    static isOnRequestedRoute(requestedUrl: string, currentUrl: string): boolean {
        const withoutTrailingSlash = (path: string) => (path.length > 1 ? path.replace(/\/$/, '') : path);
        const requestedAbsolute = requestedUrl.startsWith('http') ? new URL(requestedUrl) : new URL(requestedUrl, currentUrl);
        const requested = withoutTrailingSlash(requestedAbsolute.pathname);
        const current = withoutTrailingSlash(new URL(currentUrl).pathname);
        // A child of the requested route still counts as being on it. Exact equality reported the app's own
        // navigation as drift: an exercise-details url legitimately becomes
        // `.../exercises/programming-exercises/<id>/code-editor/<participationId>` once the editor opens, and
        // restoreRouteIfDrifted then re-navigated to the parent, the app re-navigated back, and the caller spent
        // its whole polling budget on that churn before failing with a message blaming routing.
        //
        // Deliberately not relaxed further: "any path that is not /courses" would report an authentication
        // redirect as a successful restore, and the caller would then poll for content it can never reach.
        return current === requested || current.startsWith(requested + '/');
    }

    /**
     * Detects the specific lazy-chunk-load fallback where Angular routes the page to a bare
     * `/courses` after a navigation to a different intended URL. Returns true only when
     * the caller-requested URL was NOT itself the bare `/courses` and the current URL has
     * resolved to exactly that fall-back.
     *
     * Public so the single source of truth for this heuristic can be reused by the
     * `page.goto` render-check wrapper in `support/fixtures.ts` rather than duplicating it.
     */
    static driftedToCoursesFallback(requestedUrl: string, currentUrl: string): boolean {
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

    /**
     * How long a restored route gets to prove it stayed off the `/courses` fallback.
     * <p>
     * Short on purpose: this only covers the router resolving the lazy route it was just sent to, and
     * the common case — the navigation landed where it was asked to — returns immediately.
     */
    private static readonly ROUTE_RESTORE_TIMEOUT = 15_000;

    /**
     * How long the page has to stay on a route before it counts as restored.
     * <p>
     * The drift this whole mechanism guards against happens *after* the document has loaded: the router
     * resolves the lazy route, the chunk fails, and only then does the URL change to `/courses`. Reading the
     * URL once therefore reports success for a page that is about to leave the route again.
     * <p>
     * Kept short because a healthy reload pays it too, and because the fallback redirect follows the load
     * closely rather than arbitrarily later.
     */
    private static readonly ROUTE_SETTLE_TIMEOUT = 1_000;

    /**
     * Whether the page is on the requested route and is still on it {@link ROUTE_SETTLE_TIMEOUT} later.
     * <p>
     * `waitForURL` resolves the moment its predicate holds, so it cannot express "and keeps holding". Waiting
     * for the opposite does: a redirect away resolves the wait, and nothing happening times out. Playwright
     * reports the router's same-document navigation, so the fallback redirect is observed rather than missed.
     *
     * @param page        the page to observe
     * @param expectedUrl the url the caller asked for
     */
    private static holdsRequestedRoute = async (page: Page, expectedUrl: string): Promise<boolean> => {
        if (!Commands.isOnRequestedRoute(expectedUrl, page.url())) {
            return false;
        }
        try {
            await page.waitForURL((url) => !Commands.isOnRequestedRoute(expectedUrl, url.toString()), { timeout: Commands.ROUTE_SETTLE_TIMEOUT });
            return false;
        } catch (error) {
            // Only the timeout means the route held. Anything else — a closed page above all — is not a
            // restored route, and reporting one would send the caller back to polling for content it can
            // never see.
            return error instanceof errors.TimeoutError;
        }
    };

    /**
     * Re-navigates to {@link expectedUrl} if the page has landed on the bare `/courses` fallback instead,
     * and reports whether the page is on the expected route afterwards.
     * <p>
     * A reload re-bootstraps the SPA from scratch. When a lazy route chunk fails to resolve — a known
     * symptom under heavy parallel CI load — the router falls back to `/courses`, and nothing ever
     * navigates back. Any assertion about route-specific content then waits for an element that cannot
     * exist, and every further reload re-loads the fallback, so a retry loop makes it worse rather than
     * better. Restoring the route explicitly turns that dead end into one more attempt.
     * <p>
     * The document being loaded is not the readiness signal to wait for: `load` fires once the shell and
     * its resources have arrived, while the router may still be resolving the lazy route — and it lands
     * back on the fallback when that resolution fails again. The URL is what separates a restored route
     * from a page that merely finished loading, so that is what this waits on, and it has to still be the
     * requested one a moment later rather than only at the instant it is read.
     */
    static restoreRouteIfDrifted = async (page: Page, expectedUrl: string): Promise<boolean> => {
        if (await Commands.holdsRequestedRoute(page, expectedUrl)) {
            return true;
        }
        // Deliberately not "anything that is not the fallback": an authentication redirect, or any other route
        // the app decides on, would otherwise be reported as a successful restore, and the caller would keep
        // polling for route-specific content that cannot appear there.
        await page.goto(expectedUrl);
        await page.waitForLoadState('load');
        const arrived = await page
            .waitForURL((url) => Commands.isOnRequestedRoute(expectedUrl, url.toString()), { timeout: Commands.ROUTE_RESTORE_TIMEOUT })
            .then(() => true)
            .catch(() => false);
        // Arriving is not the same as staying: the re-navigation can hit the same lazy-chunk failure and drop
        // back to the fallback, which is precisely the case the caller needs reported as a failed restore.
        return arrived && (await Commands.holdsRequestedRoute(page, expectedUrl));
    };

    /**
     * Reloads the page, then restores the route if the reload drifted to the `/courses` fallback.
     * Returns whether the page ended up on the expected route.
     * <p>
     * Pass {@link expectedUrl} when reloading in a loop: the URL the loop started on is the one to
     * return to, whereas the URL immediately before a later reload may already be the fallback.
     * Defaults to the current URL, which is what a single reload wants.
     *
     * @see restoreRouteIfDrifted for why the fallback is a dead end without this.
     */
    static reloadAndRestoreRoute = async (page: Page, expectedUrl?: string): Promise<boolean> => {
        const targetUrl = expectedUrl ?? page.url();
        await page.reload();
        return Commands.restoreRouteIfDrifted(page, targetUrl);
    };

    static reloadUntilFound = async (page: Page, locator: Locator, interval = 10000, timeout = 60000) => {
        const startTime = Date.now();
        // The route this loop is polling on. Every reload returns here, so a drift to the /courses
        // fallback costs one attempt instead of the whole timeout budget.
        const requestedUrl = page.url();
        let failedRouteRestores = 0;

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
                    if (!(await Commands.reloadAndRestoreRoute(page, requestedUrl))) {
                        failedRouteRestores++;
                    }
                } catch (reloadError) {
                    throw new Error(`Failed to reload or restore the page while waiting for element: ${reloadError}`, { cause: reloadError });
                }
            }
        }

        // Name both routes and any failed restores: a final URL that is not the requested one, or a
        // restore that never stuck, is the signal that the SPA could not load this route at all — which
        // is otherwise indistinguishable from an element that simply never appeared.
        const restoreNote = failedRouteRestores > 0 ? `, route restore failed ${failedRouteRestores} time(s)` : '';
        throw new Error(`Timed out finding an element matching the "${locator}" locator (requested URL: ${requestedUrl}, final URL: ${page.url()}${restoreNote})`);
    };

    static reloadUntilTextFound = async (page: Page, locator: Locator, expectedText: string | RegExp, interval = 5000, timeout = 60000) => {
        const startTime = Date.now();
        let lastSeenText: string | null = null;
        const matches = (text: string | null): boolean => text != null && (expectedText instanceof RegExp ? expectedText.test(text) : text.includes(expectedText));
        // The route this loop is polling on, for the same reason as in reloadUntilFound: a reload that drifts to the
        // /courses fallback is a dead end, because every later reload then reloads the fallback and the text being
        // waited for lives on a route the page is no longer on.
        const requestedUrl = page.url();
        let failedRouteRestores = 0;

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
                if (!(await Commands.reloadAndRestoreRoute(page, requestedUrl))) {
                    failedRouteRestores++;
                }
            } catch (reloadError) {
                throw new Error(`Failed to reload or restore the page while waiting for text "${expectedText}": ${reloadError}`, { cause: reloadError });
            }
        }

        const restoreNote = failedRouteRestores > 0 ? `, route restore failed ${failedRouteRestores} time(s)` : '';
        throw new Error(
            `Timed out waiting for text "${expectedText}" in locator "${locator}" (requested URL: ${requestedUrl}, final URL: ${page.url()}${restoreNote}). Last seen text: "${lastSeenText}"`,
        );
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
    /**
     * Waits until an exercise's participation stops gaining build results.
     *
     * waitForExerciseBuildToFinish returns as soon as one more result than it started with exists, which is not the
     * same as the exercise being idle. An exam programming exercise receives two builds - the scheduled
     * after-due-date one and an on-demand instructor trigger - so the first return can leave a second result still
     * being written. Anything that then mutates the participation, such as submitting a manual assessment, races
     * that write and is rejected.
     *
     * @param quietMs how long the result count has to stay unchanged before the exercise counts as settled
     */
    static waitForExerciseResultsToSettle = async (
        page: Page,
        exerciseAPIRequests: ExerciseAPIRequests,
        exerciseId: number,
        quietMs: number = 8000,
        interval: number = POLLING_INTERVAL,
        timeout: number = BUILD_FINISH_TIMEOUT,
    ) => {
        const countResults = (participation: StudentParticipation | undefined): number =>
            participation?.submissions ? participation.submissions.reduce((sum, submission) => sum + (submission.results?.length ?? 0), 0) : 0;

        const startTime = Date.now();
        let lastCount: number | undefined;
        let lastChange = Date.now();

        while (Date.now() - startTime < timeout) {
            let count: number | undefined;
            try {
                count = countResults(await exerciseAPIRequests.getProgrammingExerciseParticipation(exerciseId));
            } catch {
                // A read that failed says nothing about whether results are still arriving, so it must not count
                // towards the quiet window. Restart the window instead: only an unchanged count that was actually
                // read is evidence that the builds have stopped.
                lastChange = Date.now();
            }
            if (count !== undefined) {
                if (count !== lastCount) {
                    lastCount = count;
                    lastChange = Date.now();
                } else if (Date.now() - lastChange >= quietMs) {
                    return count;
                }
            }
            await new Promise((resolve) => setTimeout(resolve, interval));
        }
        throw new Error(`Exercise ${exerciseId} kept producing build results for ${timeout}ms and never settled (last count: ${lastCount ?? 'never read'})`);
    };

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
     * @param initialResultId - Latest result ID before the build starts. Pass null when there was no previous result; omit to let this helper take the snapshot.
     */
    static waitForParticipationBuildToFinish = async (
        exerciseAPIRequests: ExerciseAPIRequests,
        participationId: number,
        interval: number = POLLING_INTERVAL,
        timeout: number = BUILD_FINISH_TIMEOUT,
        initialResultId?: number | null,
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
        if (initialResultId === undefined) {
            try {
                const participation = await exerciseAPIRequests.getParticipationWithLatestResult(participationId);
                initialResultId = getLatestResultId(participation) ?? null;
            } catch {
                // ignore — we will poll until we see a new result ID
                initialResultId = null;
            }
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
