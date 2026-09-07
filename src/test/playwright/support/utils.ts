import dayjs from 'dayjs';
import type { Dayjs as ModelDayjs } from 'dayjs/esm';
import utc from 'dayjs/plugin/utc';
import { v4 as uuidv4 } from 'uuid';
import { DATE_TIME_PICKER_FORMAT, Exercise, ExerciseType, ProgrammingExerciseAssessmentType, ProgrammingLanguage, TIME_FORMAT } from './constants';
import * as fs from 'fs';
import { dirname } from 'path';
import { Browser, BrowserContext, Locator, Page, Request, Response, expect } from '@playwright/test';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamAPIRequests } from './requests/ExamAPIRequests';
import { ExerciseAPIRequests } from './requests/ExerciseAPIRequests';
import { ExamExerciseGroupCreationPage } from './pageobjects/exam/ExamExerciseGroupCreationPage';
import { ModelingEditor } from './pageobjects/exercises/modeling/ModelingEditor';
import { OnlineEditorPage } from './pageobjects/exercises/programming/OnlineEditorPage';
import { MultipleChoiceQuiz } from './pageobjects/exercises/quiz/MultipleChoiceQuiz';
import { TextEditorPage } from './pageobjects/exercises/text/TextEditorPage';
import { ExamNavigationBar } from './pageobjects/exam/ExamNavigationBar';
import { ExamStartEndPage } from './pageobjects/exam/ExamStartEndPage';
import { ExamParticipationPage } from './pageobjects/exam/ExamParticipationPage';
import { Commands } from './commands';
import { admin, studentOne } from './users';
import cPartiallySuccessful from '../fixtures/exercise/programming/c/partially_successful/submission.json';
import { ExamManagementPage } from './pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from './pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from './pageobjects/assessment/ExerciseAssessmentDashboardPage';

// Add utc plugin to use the utc timezone
dayjs.extend(utc);

/*
 * This file contains all the global utility functions.
 */

/**
 * Hands a date from the suite over to one of the Angular app's models.
 *
 * The app is built against dayjs' ESM entry point and this suite against its CommonJS one. Both describe the very
 * same object at run time, but the compiler sees two unrelated `Dayjs` types, and resolving the suite to the ESM
 * build is not an option: Playwright loads these files through Node, which cannot read that build. Naming the
 * crossing once here keeps it out of every call site.
 *
 * @param date a date created by the suite
 * @returns the same date, typed the way the app's models expect it
 */
export function asModelDate(date: dayjs.Dayjs): ModelDayjs {
    return date as unknown as ModelDayjs;
}

/**
 * True for the Chrome DevTools Protocol body-eviction error, i.e.
 * `response.json: Protocol error (Network.getResponseBody): No data found for resource ...`.
 */
function isResponseBodyEvicted(error: unknown): boolean {
    const message = error instanceof Error ? error.message : String(error);
    return message.includes('getResponseBody') || message.includes('No data found for resource');
}

/**
 * Node-held bodies of non-GET /api responses, captured by {@link installApiResponseCapture}.
 * Keyed by the Request instance — the same object `readResponseJson` sees via `response.request()`,
 * so lookups are exact and entries are garbage-collected with their Request. Node memory is immune
 * to Chromium's DevTools buffer eviction, making this the only reliable source for non-GET
 * create/update/delete bodies, which must never be replayed read-side (side effects).
 */
const capturedApiResponseBodies = new WeakMap<Request, Buffer>();

/**
 * In-flight reads of response bodies for requests we continued instead of replaying (see
 * {@link captureBodyWithoutReplaying}). Keyed by the same Request instance {@link readResponseJson}
 * sees via `response.request()`.
 */
const pendingApiResponseBodies = new WeakMap<Request, Promise<Buffer | undefined>>();

/**
 * Largest multipart request body we re-issue from Node in {@link installApiResponseCapture}, inclusive:
 * a body of exactly this size is still captured, anything larger is not.
 * Multipart requests up to this size are the metadata-carrying ones whose response bodies tests
 * actually read — course create/update post a small JSON blob plus an optional course icon. Genuine
 * large file uploads stay on `route.continue()`: buffering megabytes through Node costs memory and
 * buys nothing, because those tests do not read the response body.
 */
const MAX_CAPTURED_MULTIPART_BODY_BYTES = 1024 * 1024;

/**
 * Size of a request body in bytes, or `undefined` when it cannot be determined. Prefers the
 * `content-length` header (already parsed, no buffer materialisation) and falls back to the post
 * data. An unknown size is treated as "too large" by the caller, keeping the conservative default.
 */
function requestBodySizeInBytes(request: Request): number | undefined {
    const contentLength = Number(request.headers()['content-length']);
    if (Number.isFinite(contentLength) && contentLength >= 0) {
        return contentLength;
    }
    return request.postDataBuffer()?.length;
}

/**
 * Whether `route.fetch()` can faithfully re-send this request's multipart body.
 *
 * `route.fetch()` replays the body Playwright holds in Node, i.e. `postDataBuffer()`. For a
 * `FormData` assembled purely in memory (a JSON blob, a cropped-image blob) that buffer is
 * byte-complete. For a part backed by a **file on disk** — anything a test attaches with
 * `setInputFiles()` — it is not: Chromium streams those parts from disk and never hands the bytes to
 * the driver, so the buffer contains the part's headers but an empty payload (387 B for an 11 KB
 * PDF). Replaying that sends a part with a `filename` and no content, and the server rejects it —
 * `FileUploadSubmissionResource` answers 400 "The uploaded file is empty", which made the
 * file-upload participation and assessment tests fail deterministically.
 *
 * Detect exactly that signature: a part declaring a `filename` whose payload is empty. Note we
 * cannot compare against `content-length` — Chromium does not expose it on these requests (it is
 * added further down the network stack), so it reads as `undefined` for in-memory `FormData` too and
 * would disable the capture wholesale, reopening the eviction gap it exists to close.
 *
 * Anything unparseable is treated as not replayable, so the caller falls back to `route.continue()`:
 * the browser then sends the untouched request and we merely forgo the Node-held response body,
 * which degrades robustness instead of corrupting the upload.
 */
function isBodyFaithfullyReplayable(request: Request): boolean {
    const body = request.postDataBuffer();
    if (!body) {
        return false;
    }
    const boundary = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(request.headers()['content-type'] ?? '');
    const delimiter = (boundary?.[1] ?? boundary?.[2])?.trim();
    if (!delimiter) {
        return false;
    }
    // latin1 keeps one char per byte, so payload lengths measured here are byte-exact.
    const segments = body.toString('latin1').split(`--${delimiter}`);
    return segments.every((segment) => {
        const headerEnd = segment.indexOf('\r\n\r\n');
        if (headerEnd === -1) {
            return true; // preamble, epilogue or a segment without headers: nothing to verify
        }
        if (!/;\s*filename\s*=/i.test(segment.slice(0, headerEnd))) {
            return true; // plain field, always carried in full
        }
        return segment.slice(headerEnd + 4).replace(/\r\n$/, '').length > 0;
    });
}

/**
 * Hold the response body for a request we deliberately did NOT replay through `route.fetch()`.
 *
 * Skipping the replay keeps a file-backed upload intact, but it also gives up the Node-held body that
 * {@link readResponseJson} relies on — and a non-GET response cannot be recovered read-side, because
 * replaying it would repeat the side effect. Under parallel CI load Chromium then evicts the body from
 * its bounded per-renderer network buffer before the test reads it, which failed the file-upload
 * submission POST and the drag-and-drop quiz creation POST (its background image is a disk-backed file,
 * so it takes this same path). Reading the body here, as soon as the response arrives, closes that gap
 * without touching the request the browser sent.
 *
 * Best-effort by design: any failure leaves the entry absent and `readResponseJson` behaves exactly as
 * it would have without this call, so this can only ever add robustness.
 */
function captureBodyWithoutReplaying(request: Request): void {
    const read = request
        .response()
        .then((response) => (response && response.status() < 300 ? response.body() : undefined))
        .catch(() => undefined);
    // Store the in-flight read, not its result: `waitForResponse` resolves at the same moment this
    // promise is created, so a test that immediately calls readResponseJson would otherwise race ahead
    // of the buffer being stored, issue its own second CDP read, and hit the eviction anyway. Handing
    // out the promise makes the test await this single earliest-possible read.
    pendingApiResponseBodies.set(request, read);
}

/**
 * Capture non-GET /api response bodies at the network layer for a whole browser context:
 * `route.fetch()` performs the request from Node, we keep the body in Node memory for
 * {@link readResponseJson}, and fulfill the page with the same response. This only works because
 * `serviceWorkers: 'block'` (playwright.config.ts) keeps the Angular service worker from handling
 * /api fetches — Playwright routing never sees service-worker-handled requests, which is what
 * defeated an earlier page-scoped version of this capture.
 *
 * Scope guards: only `/api/` URLs are routed at all (see the glob below — it must stay a string so
 * Playwright does not widen interception to every request), GETs are continued untouched (SSE — GET
 * text/event-stream, e.g. Iris — must not be fetched from Node, and GET evictions are recoverable
 * read-side by replay), and multipart bodies
 * are only captured up to {@link MAX_CAPTURED_MULTIPART_BODY_BYTES} inclusive **and** only when
 * Playwright's copy of the body is byte-complete (see {@link isBodyFaithfullyReplayable}). Multipart
 * was previously skipped outright, which left course create/update (Angular posts them as `FormData`)
 * with no Node-held body: a POST/PUT cannot be replayed read-side, so an eviction there fails the test
 * outright rather than degrading. `route.fetch()` re-sends the body buffer Playwright holds and
 * preserves the `content-type` header including its multipart boundary — so for an in-memory
 * `FormData` the server sees the same request, but a file-backed part would arrive empty, which is
 * exactly what the fidelity guard excludes.
 *
 * Error semantics matter here: `route.continue()` is only safe while the request has NOT been
 * dispatched. Once `route.fetch()` has sent the request to the server, any failure afterwards must
 * abort the routed request — continuing would dispatch it a second time and duplicate a
 * non-idempotent side effect (e.g. create a second entity).
 */
export async function installApiResponseCapture(context: BrowserContext): Promise<void> {
    await context.route(
        // A STRING GLOB, deliberately — not the equivalent `(url) => url.pathname.includes('/api/')`
        // predicate. Playwright can only push a URL pattern down to the browser when EVERY matcher
        // registered on the context is a string: `RouteHandler.prepareInterceptionPatterns` sets
        // `all = true` for any function/RegExp matcher and then returns `[{ glob: '**/*' }]`. With the
        // predicate, every request in every E2E context therefore round-tripped through this handler
        // just to be waved through — measured over 12 Iris test attempts: 20334 `route.continue()`
        // calls with `**/*` versus 326 with this glob, i.e. ~1700 driver round-trips per attempt of
        // pure overhead. That is dead weight everywhere and it is not free on a CPU-saturated CI
        // runner (issue #13383).
        //
        // What this does NOT buy: HTTP caching. Playwright disables Chromium's cache whenever any
        // interception is registered at all (`_updateProtocolRequestInterceptionForSession` sends
        // `Network.setCacheDisabled: <interception enabled>`), so narrowing the pattern changes
        // nothing there — the same 12 attempts re-fetched the `max-age=31536000,immutable`
        // `vite/deps/*` bundles 24x either way. Restoring cacheability would mean not routing these
        // contexts at all, which is a separate question from this pattern.
        '**/api/**',
        async (route) => {
            const request = route.request();
            // The glob above is the browser-side filter; this is the exact predicate it approximates,
            // so a URL that merely resembles an API path can never take the capture path below.
            if (request.method() === 'GET' || !new URL(request.url()).pathname.includes('/api/')) {
                await route.continue();
                return;
            }
            const requestContentType = request.headers()['content-type'] ?? '';
            if (requestContentType.includes('multipart/form-data')) {
                const bodySize = requestBodySizeInBytes(request);
                // Size cap first (cheap), then the fidelity check: a file-backed part is invisible to
                // Playwright, so replaying it would upload an empty file. See isBodyFaithfullyReplayable.
                if (bodySize === undefined || bodySize > MAX_CAPTURED_MULTIPART_BODY_BYTES || !isBodyFaithfullyReplayable(request)) {
                    await route.continue();
                    // Fire-and-forget: awaiting here would hold the route handler open until the response
                    // arrives, delaying Playwright's routing for no benefit — the read cannot start earlier.
                    void captureBodyWithoutReplaying(request);
                    return;
                }
            }
            let apiResponse;
            try {
                // maxRedirects: 0 — fulfill the page with the raw response (including any 3xx) so the
                // browser handles redirects itself; Node must not transparently follow a non-GET redirect.
                apiResponse = await route.fetch({ maxRedirects: 0 });
            } catch {
                // route.fetch() rejected. We cannot distinguish a pre-dispatch failure from a transport
                // failure that occurred after the server already received (and possibly executed) the
                // request, so route.continue() is unsafe here — it would re-dispatch and could duplicate a
                // non-idempotent side effect (e.g. create a second entity). Per the invariant documented
                // above, any failure once route.fetch() has been called must abort; the page then sees a
                // network error and Playwright retries the test.
                await route.abort('failed').catch(() => {});
                return;
            }
            try {
                capturedApiResponseBodies.set(request, await apiResponse.body());
                await route.fulfill({ response: apiResponse });
            } catch {
                // The server has already executed the request via route.fetch(); continue() would dispatch
                // it a second time. Abort so the page sees a network error instead of a duplicated action.
                await route.abort('failed').catch(() => {});
            }
        },
    );
}

/**
 * Read a Playwright {@link Response} body as JSON, resilient to Chrome's CDP
 * "Network.getResponseBody: No data found for resource" failure.
 *
 * Two mechanisms feed this (see {@link installApiResponseCapture} and baseFixtures):
 * `serviceWorkers: 'block'` removes the service-worker-served responses whose bodies CDP frequently
 * cannot return at all, and the network-layer capture holds every non-GET /api body in Node memory.
 * What remains is the rare genuine eviction of a just-arrived body from Chrome's bounded
 * per-renderer network buffer under parallel E2E load. This helper hardens the common
 * `await response.json()` pattern:
 *   0. use the Node-held captured body when present — immune to CDP eviction;
 *   1. read the body as JSON (fast path — the eager response-event read in baseFixtures usually
 *      already memoized the buffer in Node);
 *   2. on an eviction error, re-read the raw body once — catches a transient (non-eviction) CDP hiccup;
 *   3. for idempotent **GET** requests, replay the request to fetch a fresh body — the only read-side
 *      recovery from a true eviction (a non-idempotent request must not be replayed: it would repeat
 *      the side effect, e.g. create a second entity);
 *   4. for a non-GET, use the caller's `recoverIdempotently` callback if one was supplied — see below;
 *   5. otherwise throw a clear, retryable error so Playwright's test-level retry can absorb it.
 *
 * `recoverIdempotently` exists for one unavoidable gap. A multipart request with a **file-backed** part
 * cannot be replayed from Node (Chromium streams those bytes from disk and never hands them to the
 * driver), so its response body lives only in Chrome and the capture above has to fall back to a CDP
 * read. When the page then navigates — as the quiz editor does on a successful save — Chrome discards
 * the body of the document being left, and CDP answers "Response body is not available for a response
 * that was navigated away from". That is a race no read-side retry can win, because the bytes are gone.
 * A caller that can re-derive the same information with an **idempotent GET** (looking the just-created
 * entity up by title, say) passes a callback here and stops depending on the discarded body.
 * Only ever pass something side-effect-free: it runs in place of reading a response, not in place of
 * making the request.
 *
 * Historical note: an enlarged CDP network buffer and whole-run body retention were both reverted
 * (they OOM-crashed Chromium under parallel CI load) — do not reintroduce those. A page-scoped
 * route capture was also once removed because the service worker bypassed it; the context-scoped
 * capture above works only in combination with `serviceWorkers: 'block'`.
 */
export async function readResponseJson<T = any>(response: Response, recoverIdempotently?: () => Promise<T>): Promise<T> {
    const capturedBody = capturedApiResponseBodies.get(response.request());
    if (capturedBody) {
        return JSON.parse(capturedBody.toString('utf-8')) as T;
    }
    // A request we deliberately continued rather than replayed (a file-backed upload) has no Node-held
    // body, but its read was started the moment the response arrived. Await that one instead of racing it.
    const pendingBody = await pendingApiResponseBodies.get(response.request());
    if (pendingBody) {
        return JSON.parse(pendingBody.toString('utf-8')) as T;
    }
    try {
        return (await response.json()) as T;
    } catch (error) {
        if (!isResponseBodyEvicted(error)) {
            throw error;
        }
        try {
            return JSON.parse((await response.body()).toString('utf-8')) as T;
        } catch (bodyError) {
            if (!isResponseBodyEvicted(bodyError)) {
                throw bodyError;
            }
        }
        const request = response.request();
        if (request.method() === 'GET') {
            const replay = await response.frame().page().request.fetch(request);
            return (await replay.json()) as T;
        }
        if (recoverIdempotently) {
            return await recoverIdempotently();
        }
        throw new Error(
            `Response body for ${request.method()} ${request.url()} was evicted from Chrome's network buffer before it could be read ` +
                `(CDP Network.getResponseBody). A non-idempotent response cannot be recovered read-side; failing so Playwright retries.`,
            { cause: error },
        );
    }
}

/**
 * Generates a unique identifier.
 */
export function generateUUID() {
    const uuid = uuidv4().replace(/-/g, '');
    return uuid.substr(0, 9);
}

/**
 * Allows to enter date into the UI
 */
export async function enterDate(page: Page, selector: string, date: dayjs.Dayjs) {
    await fillDateTimePicker(page.locator(selector).locator('#date-input-field'), date);
}

/**
 * Types a date into a PrimeNG p-datepicker input (the `jhi-date-time-picker` wrapper).
 *
 * The picker must be driven with real keystrokes: its `onUserInput` handler ignores any `input`
 * event that is not preceded by a `keydown` (an `isKeydown` guard), so Playwright's `fill()` — which
 * sets the value without keyboard events — is silently dropped. We clear the field and type the value
 * in the picker's display format (DD.MM.YYYY HH:mm), then tab out to commit it to the form model.
 */
export async function fillDateTimePicker(dateInputField: Locator, date: dayjs.Dayjs, format: string = DATE_TIME_PICKER_FORMAT) {
    const expectedValue = date.format(format);
    await expect(dateInputField).toBeEnabled();
    // PrimeNG's masked datepicker input can still drop the first keystroke after a clear while the
    // mask/focus state is settling (worse under load) — e.g. "0.09.2027" instead of "20.09.2027".
    // Retry the whole clear+type until the field holds the expected value (web-first, self-healing).
    await expect(async () => {
        await dateInputField.click();
        // Wait until the input is actually focused before typing; clear via keyboard so focus is kept.
        await expect(dateInputField).toBeFocused();
        await dateInputField.press('ControlOrMeta+a');
        await dateInputField.press('Delete');
        // Ensure the clear has actually settled before typing, so the first keystroke is not swallowed while the
        // mask is still resetting (the root cause of the dropped leading character).
        await expect(dateInputField).toHaveValue('');
        // PrimeNG's onUserInput only reacts to input events preceded by a keydown, so type real
        // keystrokes; a small per-key delay keeps the picker from dropping characters under load.
        await dateInputField.pressSequentially(expectedValue, { delay: 30 });
        expect(await dateInputField.inputValue()).toBe(expectedValue);
    }).toPass({ timeout: 15000 });
    await dateInputField.press('Tab');
}

/**
 * Formats the day object with the time format which the server uses. Also makes sure that day uses the utc timezone.
 * @param day the day object
 * @returns a formatted string representing the date with utc timezone
 */
export function dayjsToString(day: dayjs.Dayjs) {
    // We need to add the Z at the end. Otherwise, the server can't parse it.
    return day.utc().format(TIME_FORMAT) + 'Z';
}

export const BUILD_AND_TEST_AFTER_DUE_DATE_BUFFER_SECONDS = 10;

/** The grace period `prepareExam` configures, kept short so tests do not have to wait out the server default of 180s. */
const EXAM_GRACE_PERIOD_IN_SECONDS = 10;

export function getExamBuildAndTestAfterDueDate(exam: Exam) {
    return getExamEndDateWithGrace(exam).add(BUILD_AND_TEST_AFTER_DUE_DATE_BUFFER_SECONDS, 'seconds');
}

export function getExamEndDateWithGrace(exam: Exam) {
    const gracePeriodSeconds = exam.gracePeriod ?? 0;
    return dayjs(exam.endDate as any).add(gracePeriodSeconds, 'seconds');
}

export async function waitForExamBuildAndTestAfterDueDate(exam: Exam, page: Page) {
    // For exam programming exercises the score-producing build "test" phase runs only AFTER_DUE_DATE, which
    // the server schedules at dueDate + 15 min (the intended default; see
    // AutomaticAfterDueDateService.BUILD_AND_TEST_OFFSET_MINUTES). Instead of waiting that long, trigger the
    // instructor build-and-test for the exam's programming exercise on demand: by the time this is called the
    // student's individual working period is already over, so the AFTER_DUE_DATE-gated phase runs and produces
    // the score immediately. This is a no-op when the exam has no programming exercise. We authenticate as
    // admin first so the helper works regardless of the caller's current auth state (some callers, e.g. the
    // ExamResults "Assess all submissions" beforeAll, invoke it on a fresh page before logging in).
    await Commands.login(page, admin);
    const examAPIRequests = new ExamAPIRequests(page);
    const exerciseAPIRequests = new ExerciseAPIRequests(page);
    const exerciseGroups = await examAPIRequests.getExerciseGroups(exam);
    const programmingExercise = exerciseGroups.flatMap((group) => group.exercises ?? []).find((exercise) => (exercise.type as string) === ExerciseType.PROGRAMMING);
    if (!programmingExercise?.id) {
        return;
    }
    await exerciseAPIRequests.triggerInstructorBuildForAll(programmingExercise.id);
    await Commands.waitForExerciseBuildToFinish(page, exerciseAPIRequests, programmingExercise.id);
    // Two builds are in flight here: the after-due-date one the server scheduled for ten seconds after the exam
    // ended, and the instructor trigger above. The wait returns after whichever lands first, so without settling
    // the second result is still being written while the caller starts assessing - and the manual submit is then
    // rejected with a 404 or 409 that surfaces much later as a wrong-score assertion.
    await Commands.waitForExerciseResultsToSettle(page, exerciseAPIRequests, programmingExercise.id);
    // The "Run Tests after Due Date" date does not have to be moved here: the exercise is created with it set to
    // `getExamBuildAndTestAfterDueDate(exam)`, which is ten seconds after the exam ends with its grace period, and
    // this helper only runs once the exam is over. Writing it again through the timeline endpoint used to be part of
    // this helper and is what made every exam programming assessment fail outside UTC: that endpoint stores the date
    // shifted by the server's UTC offset, so on a UTC+2 machine the date landed two hours in the future, the
    // assessment dashboard reported that the tests were still pending, and no submission was ever offered to assess.
}

/**
 * This function is necessary to make the server and the client date comparable.
 * Dates are entered through the PrimeNG p-datepicker, which is minute-precision (it has no seconds
 * field), so the persisted value is always truncated to the minute. We therefore compare only down
 * to the minute (YYYY-MM-DDTHH:mm) and ignore seconds/milliseconds, which also avoids the server's
 * varying millisecond digit count.
 * @param date the date as a string
 * @returns a date string trimmed to minute precision
 */
export function trimDate(date: string) {
    return date.slice(0, 16);
}

/**
 * Converts a snake_case word to Title Case (each word's first letter capitalized and spaces in between).
 * @param str - The snake_case word to be converted to Title Case.
 * @returns The word in Title Case.
 */
export function titleCaseWord(str: string) {
    str = str.replace('_', ' ');
    const sentence = str.toLowerCase().split(' ');
    for (let i = 0; i < sentence.length; i++) {
        sentence[i] = sentence[i][0].toUpperCase() + sentence[i].slice(1);
    }
    return sentence.join(' ');
}

/**
 * Retrieves the DOM element representing the exercise with the specified ID.
 * @param page - Playwright Page instance used during the test.
 * @param exerciseId - The ID of the exercise for which to retrieve the DOM element.
 * @returns Locator that yields the DOM element representing the exercise.
 */
export function getExercise(page: Page, exerciseId: number) {
    return page.locator(`#exercise-${exerciseId}`);
}

/**
 * Converts a title to lowercase and replaces spaces with hyphens.
 * @param title - The title to be converted to lowercase with hyphens.
 * @returns The converted title in lowercase with hyphens.
 */
export function titleLowercase(title: string) {
    return title.replace(' ', '-').toLowerCase();
}

/**
 * Converts a boolean value to its related icon class.
 * @param boolean - The boolean value to be converted.
 * @returns The corresponding icon class
 */
export function convertBooleanToCheckIconClass(boolean: boolean) {
    const sectionInvalidIcon = '.fa-xmark';
    const sectionValidIcon = '.fa-circle-check';
    return boolean ? sectionValidIcon : sectionInvalidIcon;
}

/**
 * Convert a base64-encoded string to a `Blob`.
 *
 * This is an adaptation of the `base64StringToBlob` function from `blob-util` library.
 * Since Playwright has no access to DOM APIs, we cannot use the one in `blob-util` library as it uses `window` object.
 *
 * Example:
 *
 * ```js
 * var blob = blobUtil.base64StringToBlob(base64String);
 * ```
 * @param base64 - base64-encoded string
 * @param type - the content type (optional)
 * @returns Blob
 */
export function base64StringToBlob(base64: string, type?: string): Blob {
    const buffer = Buffer.from(base64!, 'base64');
    return new Blob([buffer], { type });
}

export async function clearTextField(textField: Locator, page?: Page) {
    // Check if this is a Monaco editor
    const isMonaco = (await textField.locator('.monaco-editor').count()) > 0 || (await textField.evaluate((el) => el.classList.contains('monaco-editor')));

    if (isMonaco) {
        // Wait for editor to be visible
        await textField.waitFor({ state: 'visible' });
        // Click directly on the Monaco editor to focus it
        await textField.click();
    } else {
        await textField.click({ force: true });
    }

    // Small delay to ensure focus
    if (page) {
        await page.waitForTimeout(300);
    }

    // Use platform-appropriate select all shortcut
    const isMac = process.platform === 'darwin';
    const selectAllKey = isMac ? 'Meta+a' : 'Control+a';
    if (page) {
        // Use page.keyboard for consistency
        await page.keyboard.press(selectAllKey);
        await page.waitForTimeout(100);
        await page.keyboard.press('Backspace');
    } else {
        await textField.press(selectAllKey);
        await textField.press('Backspace');
    }
}

/**
 * Sets the content of a Monaco editor directly using Monaco's API.
 * This approach identifies the editor by its DOM element position to reliably find the correct editor
 * when multiple editors exist on the page.
 * @param page - Playwright Page instance
 * @param containerSelector - CSS selector for the container element that contains the Monaco editor
 * @param text - The text to set in the editor
 */
export async function setMonacoEditorContent(page: Page, containerSelector: string, text: string) {
    await setMonacoEditorContentByLocator(page, page.locator(containerSelector), text);
}

/**
 * Sets the content of a Monaco editor directly using Monaco's API.
 * This variant works with a Locator that contains the Monaco editor.
 * It identifies the editor by its DOM element position to reliably find the correct editor
 * when multiple editors exist on the page.
 * @param page - Playwright Page instance
 * @param containerLocator - Locator for the container element that contains the Monaco editor
 * @param text - The text to set in the editor
 */
// `.monaco-editor` is Monaco's own root element. Monaco renders it itself, so there is no hook to add;
// scope it through a test id on the surrounding component rather than through further Monaco classes.
export async function setMonacoEditorContentByLocator(page: Page, containerLocator: Locator, text: string) {
    // Wait for the Monaco editor to be visible
    await containerLocator.waitFor({ state: 'visible' });
    const monacoEditor = containerLocator.locator('.monaco-editor').first();
    await monacoEditor.waitFor({ state: 'visible' });

    // Wait for Monaco to be available on window (exposed by MonacoEditorService)
    await page.waitForFunction(() => (window as any).monaco?.editor, { timeout: 10000 });

    // Identify the target Monaco instance via its DOM node reference rather than
    // screen coordinates. Bounding-box matching was fragile when hydration
    // caused layout shifts — coordinates could change mid-observation and
    // silently fall through to "last editor", which is not necessarily the
    // right one when multiple editors coexist.
    const editorHandle = await monacoEditor.elementHandle();
    if (!editorHandle) {
        throw new Error('Could not resolve Monaco editor element handle');
    }

    // When called on an /edit page, the Angular component may still be hydrating the
    // form from the server response. A setValue() that races ahead of hydration gets
    // overwritten when the API response arrives (including late arrivals after our
    // debounce wait). Retry setValue until Monaco holds our text *and keeps holding
    // it* across a sustained observation window — i.e., no late hydration will
    // clobber it after we return.
    const MAX_ATTEMPTS = 8;
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        // Click on the editor to ensure it's initialized + focused
        await monacoEditor.click();
        await page.waitForTimeout(100);

        const result = await page.evaluate(
            ({ newText, editorNode }) => {
                const monaco = (window as any).monaco;
                if (!monaco || !monaco.editor) {
                    return { success: false, error: 'Monaco not available' };
                }
                const editors = monaco.editor.getEditors();
                if (editors.length === 0) {
                    return { success: false, error: 'No Monaco editors registered' };
                }
                // Deterministic match: identical or contained DOM node reference.
                // No coordinate comparisons — layout shifts don't affect this.
                const findByNode = () =>
                    editors.find((e: any) => {
                        const dom = e.getDomNode();
                        return dom && (dom === editorNode || dom.contains(editorNode) || editorNode.contains(dom));
                    });
                const targetEditor = findByNode() || editors.find((e: any) => e.hasTextFocus() || e.hasWidgetFocus()) || editors[editors.length - 1];
                if (!targetEditor) {
                    return { success: false, error: `No matching Monaco editor found (${editors.length} editors registered)` };
                }
                targetEditor.setValue(newText);
                return { success: true };
            },
            { newText: text, editorNode: editorHandle },
        );

        if (!result.success) {
            throw new Error(`Failed to set Monaco editor content: ${result.error}`);
        }

        // Sustained-value check: read Monaco's value every 300ms for 2.1s. We need
        // the value to both (a) match `text` and (b) stay that way — that catches
        // late hydration that would otherwise overwrite after we return. The first
        // 1s covers the textChanged debounce (200ms, with buffer), the rest guards
        // against deferred form population. `readValue` uses the same fallback
        // chain as `setValue` above so they can never pick different editors.
        const readValue = async () =>
            page.evaluate(
                ({ editorNode }) => {
                    const monaco = (window as any).monaco;
                    const editors = monaco?.editor?.getEditors() || [];
                    const findByNode = () =>
                        editors.find((e: any) => {
                            const dom = e.getDomNode();
                            return dom && (dom === editorNode || dom.contains(editorNode) || editorNode.contains(dom));
                        });
                    const targetEditor = findByNode() || editors.find((e: any) => e.hasTextFocus() || e.hasWidgetFocus()) || editors[editors.length - 1];
                    return targetEditor?.getValue() ?? null;
                },
                { editorNode: editorHandle },
            );

        let stable = true;
        for (let i = 0; i < 7; i++) {
            await page.waitForTimeout(300);
            const v = await readValue();
            if (v !== text) {
                stable = false;
                break;
            }
        }
        if (stable) {
            return;
        }
    }

    throw new Error(`Monaco editor did not retain the expected text after ${MAX_ATTEMPTS} attempts (likely clobbered by Angular form hydration)`);
}

export async function hasAttributeWithValue(page: Page, selector: string, value: string): Promise<boolean> {
    return page.evaluate(
        ({ selector, value }) => {
            const element = document.querySelector(selector);
            if (!element) return false;
            for (const attr of element.attributes) {
                if (attr.value === value) {
                    return true;
                }
            }
            return false;
        },
        { selector, value },
    );
}

export function parseNumber(text?: string): number | undefined {
    return text ? parseInt(text) : undefined;
}

export async function createFileWithContent(filePath: string, content: string) {
    const directory = dirname(filePath);

    if (!fs.existsSync(directory)) {
        fs.mkdirSync(directory, { recursive: true });
    }
    fs.writeFileSync(filePath, content);
}

export async function newBrowserPage(browser: Browser) {
    // serviceWorkers: 'block' mirrors the global `use` option in playwright.config.ts — manually created
    // contexts do not inherit it, and an SW-controlled page would reintroduce the getResponseBody flake.
    const context = await browser.newContext({ ignoreHTTPSErrors: true, serviceWorkers: 'block' });
    await installApiResponseCapture(context);
    const page = await context.newPage();
    await addE2EInitScript(page);
    return page;
}

/**
 * Adds init scripts that must run on every E2E page to prevent overlays from blocking
 * test interactions. This is called automatically for the main `page` fixture via
 * `baseFixtures.ts` and must also be applied to pages created by `newBrowserPage`.
 */
export async function addE2EInitScript(page: Page) {
    // Register on the context so the suppression also applies to pages created later.
    await page.context().addInitScript(() => {
        // Hide the notification popup overlay
        const injectStyle = () => {
            const style = document.createElement('style');
            style.textContent = [
                'jhi-course-notification-popup-overlay { display: none !important; }',
                // Hide the passkey setup modal overlay (PrimeNG appends it to <body>).
                // CSS backup for the localStorage suppression below.
                '.p-dialog-mask:has(.passkey-setup-dialog) { display: none !important; }',
            ].join('\n');
            document.head.appendChild(style);
        };
        if (document.head) {
            injectStyle();
        } else {
            document.addEventListener('DOMContentLoaded', injectStyle);
        }

        // Suppress the passkey setup modal by setting a far-future reminder date.
        try {
            const futureDate = new Date();
            futureDate.setFullYear(futureDate.getFullYear() + 10);
            localStorage.setItem('earliestSetupPasskeyReminderDate', JSON.stringify(futureDate));
        } catch {
            // localStorage may not be available on about:blank — safe to ignore
        }
    });
}

/**
 * Drags an element to a droppable element.
 * @param page - Playwright Page instance used during the test.
 * @param draggable - Locator of the element to be dragged.
 * @param droppable - Locator of the element to be dropped on.
 */
export async function drag(page: Page, draggable: Locator, droppable: Locator) {
    // The droppable of a drag-and-drop quiz is sized relative to its background image, which loads
    // asynchronously. Until that image has loaded the droppable is zero-sized, which Playwright
    // treats as not visible, so boundingBox() returns null and the drag coordinates below would be
    // computed from `null`. Wait for the element to be visible (a non-empty box) before reading it.
    await droppable.waitFor({ state: 'visible', timeout: 15_000 });
    const box = (await droppable.boundingBox())!;
    // By hovering over the droppable element, we ensure it's not hidden by any other element.
    await droppable.hover();
    await draggable.hover();

    await page.mouse.down();
    await droppable.scrollIntoViewIfNeeded();
    // we have to move to the left instead of the right, because otherwise the element is outside the box as the x coordinate of the bounding box seems a bit off
    await page.mouse.move(box.x - box.width / 2, box.y + box.height / 2, {
        steps: 5,
    });

    await page.mouse.up();
}

/*
 * Exam utility functions
 */

export async function prepareExam(course: Course, end: dayjs.Dayjs, exerciseType: ExerciseType, page: Page, numberOfCorrectionRounds: number = 1): Promise<Exam> {
    const examAPIRequests = new ExamAPIRequests(page);
    const exerciseAPIRequests = new ExerciseAPIRequests(page);
    const examExerciseGroupCreation = new ExamExerciseGroupCreationPage(page, examAPIRequests, exerciseAPIRequests);
    const modelingExerciseEditor = new ModelingEditor(page);
    const programmingExerciseEditor = new OnlineEditorPage(page);
    const quizExerciseMultipleChoice = new MultipleChoiceQuiz(page);
    const textExerciseEditor = new TextEditorPage(page);
    const examNavigation = new ExamNavigationBar(page);
    const examStartEnd = new ExamStartEndPage(page);
    const examParticipation = new ExamParticipationPage(
        examNavigation,
        examStartEnd,
        modelingExerciseEditor,
        programmingExerciseEditor,
        quizExerciseMultipleChoice,
        textExerciseEditor,
        page,
    );

    await Commands.login(page, admin);
    const resultDate = end.add(1, 'second');
    const examConfig = {
        course,
        startDate: dayjs(),
        endDate: end,
        numberOfCorrectionRoundsInExam: numberOfCorrectionRounds,
        examStudentReviewStart: resultDate,
        examStudentReviewEnd: resultDate.add(5, 'minutes'),
        publishResultsDate: resultDate,
        gracePeriod: EXAM_GRACE_PERIOD_IN_SECONDS,
    };
    const exam = await examAPIRequests.createExam(examConfig);
    let additionalData = {};
    switch (exerciseType) {
        case ExerciseType.PROGRAMMING:
            additionalData = {
                submission: cPartiallySuccessful,
                progExerciseAssessmentType: ProgrammingExerciseAssessmentType.SEMI_AUTOMATIC,
                programmingLanguage: ProgrammingLanguage.C,
                skipBuildResultCheck: true,
            };
            break;
        case ExerciseType.TEXT:
            additionalData = { textFixture: 'loremIpsum-short.txt' };
            break;
        case ExerciseType.QUIZ:
            additionalData = { quizExerciseID: 0 };
            break;
        case ExerciseType.FILE_UPLOAD:
            additionalData = { fileUploadFixture: 'pdf-test-file.pdf' };
            break;
    }

    const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, exerciseType, additionalData);
    await examAPIRequests.registerStudentForExam(exam, studentOne);
    await examAPIRequests.generateMissingIndividualExams(exam);
    await examAPIRequests.prepareExerciseStartForExam(exam);
    exercise.additionalData = additionalData;
    await makeExamSubmission(course, exam, exercise, page, examParticipation, examNavigation, examStartEnd);
    return exam;
}

export async function makeExamSubmission(
    course: Course,
    exam: Exam,
    exercise: Exercise,
    page: Page,
    examParticipation: ExamParticipationPage,
    examNavigation: ExamNavigationBar,
    examStartEnd: ExamStartEndPage,
) {
    await examParticipation.startParticipation(studentOne, course, exam);
    await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
    await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
    await page.waitForTimeout(1000);
    await examNavigation.handInEarly();
    await examStartEnd.finishExam();
}

/**
 * Waits for the exam to end if it hasn't already, including its grace period.
 * This is necessary because the assessment dashboard button only appears after the exam ends, and because the server
 * refuses to open an assessment until the last student can no longer hand in, which is the exam end plus the grace
 * period (see SubmissionService#checkThatAssessmentIsPossibleElseThrow). The grace period is read from the exam itself,
 * so that exams which do not configure one (and therefore get the server default of 180s) are waited out correctly.
 * @param exam - The exam to wait for, as returned by the create call
 * @param page - The Playwright page object (used for waitForTimeout)
 */
export async function waitForExamEnd(exam: Exam, page: Page) {
    const assessableFrom = getExamEndDateWithGrace(exam);
    if (assessableFrom.isAfter(dayjs())) {
        const timeToWait = assessableFrom.diff(dayjs()) + 2000; // Add 2 second buffer
        console.log(`Waiting ${timeToWait}ms for exam (including its ${exam.gracePeriod ?? 0}s grace period) to end...`);
        await page.waitForTimeout(timeToWait);
    }
}

export async function startAssessing(
    courseID: number,
    examID: number,
    timeout: number,
    examManagement: ExamManagementPage,
    courseAssessment: CourseAssessmentDashboardPage,
    exerciseAssessment: ExerciseAssessmentDashboardPage,
    toggleSecondRound: boolean = false,
    isFirstTimeAssessing: boolean = true,
) {
    await examManagement.openAssessmentDashboard(courseID, examID, timeout);
    await courseAssessment.clickExerciseDashboardButton(0, timeout);
    if (toggleSecondRound) {
        await exerciseAssessment.toggleSecondCorrectionRound();
    }
    if (isFirstTimeAssessing) {
        await exerciseAssessment.clickHaveReadInstructionsButton();
    }
    await exerciseAssessment.clickStartNewAssessment();
    exerciseAssessment.getLockedMessage();
}

/**
 * Asserts that nothing on the page can be scrolled past the Apollon canvas.
 *
 * The canvas captures the wheel, so anything parked below it inside a scrolling ancestor is
 * unreachable: the reader scrolls, the diagram zooms, and the content underneath never arrives.
 *
 * Only ancestors are checked — a panel that scrolls beside the canvas is fine, since reaching it
 * never means scrolling past the diagram. Do not call this on the exercise create/edit form, which
 * is a form first and runs the editor with Apollon's scroll lock engaged so the wheel reaches the page.
 */
export async function expectNoScrollPastApollonCanvas(page: Page) {
    const canvas = page.locator('.apollon-editor').first();
    await expect(canvas).toBeVisible();

    const overflowing = await canvas.evaluate((element) => {
        const describe = (node: Element) =>
            node.tagName.toLowerCase() +
            (node.id ? `#${node.id}` : '') +
            (typeof node.className === 'string' && node.className.trim() ? `.${node.className.trim().split(/\s+/)[0]}` : '');

        const offenders: string[] = [];
        for (let node: Element | null = element; node; node = node.parentElement) {
            const scrolls = node.scrollHeight > node.clientHeight + 1;
            if (!scrolls) {
                continue;
            }
            const overflowY = getComputedStyle(node).overflowY;
            const isScrollContainer = overflowY === 'auto' || overflowY === 'scroll' || node === document.documentElement || node === document.body;
            if (isScrollContainer) {
                offenders.push(`${describe(node)} overflows by ${node.scrollHeight - node.clientHeight}px`);
            }
        }
        return offenders;
    });

    expect(overflowing, 'content below the Apollon canvas forces the page to scroll').toEqual([]);
}
