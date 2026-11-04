import { Page, expect, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Renders every screenshot of documentation/screenshots/manifest.json against an Artemis that runs with the `demo` profile.
 *
 * Environment:
 *   BASE_URL                       Artemis client, default http://localhost:9000
 *   DOCS_SCREENSHOT_DIR            output directory, default build/docs-screenshots (relative to the repository root)
 *   DOCS_SCREENSHOT_UPDATE=true    write the images into documentation/docs instead, replacing the checked-in ones
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
    /** `data-testid` values of elements whose content changes on every run, such as time stamps. */
    mask?: string[];
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

        await page.goto(entry.route.replace('{courseId}', String(courseId)));
        await page.waitForLoadState('networkidle');
        // Transitions and the caret would make two renderings of the same page differ.
        await page.addStyleTag({ content: VOLATILE_STYLES });

        const target = update ? path.join(DOCS_ROOT, entry.image) : path.join(outputRoot, entry.image);
        fs.mkdirSync(path.dirname(target), { recursive: true });
        await page.screenshot({
            path: target,
            fullPage: entry.fullPage ?? false,
            animations: 'disabled',
            mask: (entry.mask ?? []).map((testId) => page.getByTestId(testId)),
        });
        await context.close();
    });
}
