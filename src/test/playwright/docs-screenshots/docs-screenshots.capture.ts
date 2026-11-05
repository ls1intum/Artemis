import { APIRequestContext, Page, expect, request, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Renders every screenshot of documentation/screenshots/manifest.json against an Artemis that runs with the `demo` profile.
 *
 * Environment:
 *   BASE_URL                       Artemis client, default http://localhost:9000
 *   DOCS_SCREENSHOT_DIR            output directory, default build/docs-screenshots (relative to the repository root)
 *   DOCS_SCREENSHOT_UPDATE=true    write the images into documentation/docs instead, replacing the checked-in ones
 *   DOCS_SCREENSHOT_SETTLE_MS      milliseconds to wait for chart animations before the image is taken, default 1500
 *   DOCS_SCREENSHOT_ONLY           comma separated ids, to render only those entries
 *   DOCS_SCREENSHOT_FIXED_TIME     ISO time, freezes the clock of the client. Off by default: the demo data is created relative to the
 *                                  clock of the server, so a frozen client clock that differs from it shows deadlines in the wrong state.
 *
 * Page state is made repeatable by a light theme, English, a fixed time zone and reduced motion (see playwright.config.ts), disabled
 * transitions, and the `mask` entries of the manifest, which paint volatile elements over before the image is taken.
 */

interface ManifestEntry {
    id: string;
    image: string;
    user: string;
    route: string;
    viewport?: { width: number; height: number };
    deviceScaleFactor?: number;
    fullPage?: boolean;
    /** CSS selector of the element that the image shows, for a crop of the page such as its main card. The image is the size of the element. */
    selector?: string;
    /** With `selector`: CSS pixels of the page that are added around the element on every side, so that borders and shadows of its content are not cut off. The element has to fit into the viewport. */
    padding?: number;
    /**
     * With `selector`: CSS selector of the element where the image ends, for a part of a long form whose sections are siblings without a common wrapper. The image runs from
     * the top of `selector` to the top of this element, as wide as `selector`, and needs a viewport that is tall enough to show both.
     */
    until?: string;
    /** A box of the page in CSS pixels, for a crop that has no element of its own. Not combined with `selector`. */
    clip?: { x: number; y: number; width: number; height: number };
    /** CSS selector that has to be visible before the image is taken, for content that loads after the page. */
    waitFor?: string;
    /**
     * Actions that bring the page into the state that the image shows, run in order after the page has loaded. A step is an object with exactly one of
     * `fill` ({selector, value}: types the value into the field), `click` (selector of the element to click), `clickIfPresent` (the same, skipped when the element is not there, for an action that a run before has done already), `check` (selector of a checkbox to tick), `upload` ({selector, file}: chooses the file, a path from the repository root, in a file input), `select` ({selector, label}: picks the
     * option with that label) and `waitFor` (selector that has to be visible). A selector is a Playwright selector, so `text=Start` and `button:has-text("Save")` work.
     */
    steps?: ManifestStep[];
    /** `data-testid` values of elements whose content changes on every run. They are painted over. */
    mask?: string[];
    /** CSS selectors of elements whose content changes on every run. They are painted over. */
    maskSelector?: string[];
    /** CSS selectors of elements that cover the content of the image, such as a sticky footer. They are made invisible and keep their space. */
    hide?: string[];
}

interface ManifestStep {
    fill?: { selector: string; value: string };
    click?: string;
    clickIfPresent?: string;
    check?: string;
    upload?: { selector: string; file: string };
    select?: { selector: string; label: string };
    waitFor?: string;
}

interface Manifest {
    defaults: { viewport: { width: number; height: number }; deviceScaleFactor: number };
    users: Record<string, string>;
    screenshots: ManifestEntry[];
}

const REPOSITORY_ROOT = path.resolve(__dirname, '..', '..', '..', '..');
const MANIFEST_PATH = path.join(REPOSITORY_ROOT, 'documentation', 'screenshots', 'manifest.json');
const DOCS_ROOT = path.join(REPOSITORY_ROOT, 'documentation', 'docs');
// Transitions and the caret would make two renderings differ. The development ribbon and the footer with the branch, commit and build time
// belong to the instance that rendered the image, not to the page that the documentation describes.
const VOLATILE_STYLES = [
    '*, *::before, *::after { transition: none !important; animation: none !important; caret-color: transparent !important; }',
    'jhi-page-ribbon, jhi-footer { display: none !important; }',
].join('\n');
// Relative time stamps depend on the minute in which the demo data was created, so their text is replaced by a constant before the image is taken.
// The day of an absolute date is part of the demo data and stays, but its time of day is the minute of the seeding, so it becomes 10:00 as well.
const ABSOLUTE_TIME = /\b([A-Z][a-z]{2} \d{1,2}, \d{4})( ?-? ?)\d{1,2}:\d{2}\b/g;
const RELATIVE_TIME = /\b(Today|Yesterday|Tomorrow), \d{1,2}:\d{2}( ?[AP]M)?/g;
const RELATIVE_AGO = /\b\d+ (second|minute|hour|day|week|month)s? ago\b/g;
const CHART_SETTLE_MS = Number(process.env.DOCS_SCREENSHOT_SETTLE_MS ?? 1500);
const DEMO_PASSWORD = 'demo1234';
const DEMO_COURSE_TITLE = 'Introduction to Software Engineering';

const manifest: Manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'));
const outputRoot = path.resolve(REPOSITORY_ROOT, process.env.DOCS_SCREENSHOT_DIR ?? 'build/docs-screenshots');
const update = process.env.DOCS_SCREENSHOT_UPDATE === 'true';
const only = (process.env.DOCS_SCREENSHOT_ONLY ?? '')
    .split(',')
    .map((id) => id.trim())
    .filter(Boolean);

/** Logs in through the API, which sets the same cookie as the login form and is much less fragile than typing into it. */
async function login(page: Page, username: string): Promise<void> {
    await page.context().clearCookies();
    const response = await page.request.post('api/core/public/authenticate', { data: { username, password: DEMO_PASSWORD, rememberMe: true } });
    expect(response.status(), `login of ${username} failed, does the server run with the demo profile?`).toBe(200);
}

async function demoCourseId(page: Page): Promise<number> {
    const response = await page.request.get('api/course/courses/for-dropdown');
    expect(response.ok(), 'the course list could not be loaded').toBe(true);
    const courses: { id: number; title: string }[] = await response.json();
    const course = courses.find((candidate) => candidate.title === DEMO_COURSE_TITLE);
    expect(course, `the demo course "${DEMO_COURSE_TITLE}" does not exist, start Artemis with the demo profile`).toBeDefined();
    return course!.id;
}

/** Looks up the id of the item of the demo course with the given title. The instructor is allowed to list every kind of item, whoever the screenshot is for. */
async function itemId(api: APIRequestContext, kind: string, title: string, courseId: number): Promise<number> {
    const listings: Record<string, string> = {
        exercise: `api/course/courses/${courseId}/with-exercises`,
        exam: `api/exam/courses/${courseId}/exams`,
        lecture: `api/lecture/courses/${courseId}/lectures`,
    };
    const response = await api.get(listings[kind]);
    expect(response.ok(), `the ${kind} list of the demo course could not be loaded`).toBe(true);
    const body = await response.json();
    const items: { id: number; title: string }[] = kind === 'exercise' ? body.exercises : body;
    const item = items.find((candidate) => candidate.title === title);
    expect(item, `the demo course has no ${kind} "${title}"`).toBeDefined();
    return item!.id;
}

/** Replaces {courseId} and the {exercise:Title}, {exam:Title} and {lecture:Title} placeholders of a route by the ids of the demo course. */
async function resolveRoute(baseURL: string | undefined, route: string, courseId: number): Promise<string> {
    let resolved = route.replace('{courseId}', String(courseId));
    const placeholders = Array.from(route.matchAll(/\{(exercise|exam|lecture):([^}]+)\}/g));
    if (placeholders.length === 0) {
        return resolved;
    }
    const api = await request.newContext({ baseURL });
    try {
        const response = await api.post('api/core/public/authenticate', { data: { username: manifest.users.instructor, password: DEMO_PASSWORD, rememberMe: true } });
        expect(response.status(), 'login of the instructor failed').toBe(200);
        for (const [placeholder, kind, title] of placeholders) {
            resolved = resolved.replace(placeholder, String(await itemId(api, kind, title, courseId)));
        }
    } finally {
        await api.dispose();
    }
    return resolved;
}

for (const entry of manifest.screenshots.filter((candidate) => only.length === 0 || only.includes(candidate.id))) {
    test(`documentation screenshot ${entry.id}`, async ({ browser, baseURL }) => {
        // browser.newContext() does not inherit the options of the configuration, so the base URL has to be passed on.
        const context = await browser.newContext({
            baseURL,
            viewport: entry.viewport ?? manifest.defaults.viewport,
            deviceScaleFactor: entry.deviceScaleFactor ?? manifest.defaults.deviceScaleFactor,
            locale: 'en-US',
            timezoneId: 'Europe/Berlin',
            colorScheme: 'light',
            reducedMotion: 'reduce',
        });
        const page = await context.newPage();
        // The theme preference of a previous session must not win over the light scheme.
        await page.addInitScript(() => {
            window.localStorage.removeItem('jhi-artemisapp.theme.preference');
            // The passkey reminder is a modal that covers the page, so it is postponed far into the future, as a user does with "Remind me in 30 days".
            window.localStorage.setItem('earliestSetupPasskeyReminderDate', JSON.stringify('2099-01-01T00:00:00.000Z'));
            // The competency management opens an introduction on the first visit of a browser.
            window.localStorage.setItem('alreadyVisitedCompetencyManagement', JSON.stringify(true));
        });
        if (process.env.DOCS_SCREENSHOT_FIXED_TIME) {
            await page.clock.setFixedTime(new Date(process.env.DOCS_SCREENSHOT_FIXED_TIME));
        }

        const username = manifest.users[entry.user];
        expect(username, `the manifest has no user "${entry.user}"`).toBeDefined();
        await login(page, username);
        const courseId = await demoCourseId(page);

        await page.goto(await resolveRoute(baseURL, entry.route, courseId));
        // A page with a websocket or a poll may never be idle, so the wait is bounded; `waitFor` and the settle time below decide when the page is ready.
        // A timeout is reported in the output, so that an image of a page that is still loading is looked at before it is committed.
        await page.waitForLoadState('networkidle', { timeout: 20_000 }).catch(() => {
            console.warn(`${entry.id}: the network was not idle after 20 seconds, check the image${entry.waitFor ? '' : ' or add a `waitFor` to the entry'}`);
        });
        if (entry.waitFor) {
            await expect(page.locator(entry.waitFor).first()).toBeVisible();
        }
        // Transitions and the caret would make two renderings of the same page differ.
        await page.addStyleTag({ content: VOLATILE_STYLES });
        if (entry.hide?.length) {
            await page.addStyleTag({ content: entry.hide.map((selector) => `${selector} { visibility: hidden !important; }`).join('\n') });
        }
        for (const step of entry.steps ?? []) {
            if (step.fill) {
                await page.locator(step.fill.selector).first().fill(step.fill.value);
            } else if (step.click) {
                await page.locator(step.click).first().click();
            } else if (step.clickIfPresent) {
                // For an action that only exists the first time, such as the start of an exercise that a participation of an earlier run has started already.
                const target = page.locator(step.clickIfPresent).first();
                // `isVisible` does not wait, so wait up to the intended 3 seconds for the control to appear.
                const appeared = await target
                    .waitFor({ state: 'visible', timeout: 3_000 })
                    .then(() => true)
                    .catch(() => false);
                if (appeared) {
                    await target.click();
                }
            } else if (step.upload) {
                await page.locator(step.upload.selector).first().setInputFiles(path.join(REPOSITORY_ROOT, step.upload.file));
            } else if (step.check) {
                await page.locator(step.check).first().check();
            } else if (step.select) {
                await page.locator(step.select.selector).first().selectOption({ label: step.select.label });
            } else if (step.waitFor) {
                await expect(page.locator(step.waitFor).first()).toBeVisible();
            }
        }
        // Charts draw themselves with a script animation that no style can switch off, so the image waits until they have finished.
        await page.waitForTimeout(CHART_SETTLE_MS);
        await page.evaluate(
            ([time, ago, absolute]) => {
                const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
                for (let node = walker.nextNode(); node; node = walker.nextNode()) {
                    node.nodeValue = (node.nodeValue ?? '')
                        .replace(new RegExp(time, 'g'), '$1, 10:00')
                        .replace(new RegExp(ago, 'g'), '5 $1s ago')
                        .replace(new RegExp(absolute, 'g'), (_match, day: string, separator: string) => `${day}${separator}10:00`);
                }
            },
            [RELATIVE_TIME.source, RELATIVE_AGO.source, ABSOLUTE_TIME.source],
        );

        const target = update ? path.join(DOCS_ROOT, entry.image) : path.join(outputRoot, entry.image);
        fs.mkdirSync(path.dirname(target), { recursive: true });
        const mask = [...(entry.mask ?? []).map((testId) => page.getByTestId(testId)), ...(entry.maskSelector ?? []).map((selector) => page.locator(selector))];
        if (entry.selector && entry.until) {
            const start = page.locator(entry.selector).first();
            await start.scrollIntoViewIfNeeded();
            const startBox = (await start.boundingBox())!;
            const endBox = (await page.locator(entry.until).first().boundingBox())!;
            await page.screenshot({
                path: target,
                clip: { x: startBox.x, y: startBox.y, width: startBox.width, height: endBox.y - startBox.y },
                animations: 'disabled',
                mask,
            });
        } else if (entry.selector && entry.padding) {
            const element = page.locator(entry.selector).first();
            await element.scrollIntoViewIfNeeded();
            const box = (await element.boundingBox())!;
            const x = Math.max(0, box.x - entry.padding);
            const y = Math.max(0, box.y - entry.padding);
            await page.screenshot({
                path: target,
                clip: { x, y, width: box.x + box.width + entry.padding - x, height: box.y + box.height + entry.padding - y },
                animations: 'disabled',
                mask,
            });
        } else if (entry.selector) {
            await page.locator(entry.selector).first().screenshot({ path: target, animations: 'disabled', mask });
        } else {
            await page.screenshot({ path: target, fullPage: entry.fullPage ?? false, clip: entry.clip, animations: 'disabled', mask });
        }
        await context.close();
    });
}
