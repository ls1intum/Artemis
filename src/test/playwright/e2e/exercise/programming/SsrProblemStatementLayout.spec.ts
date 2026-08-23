import { Locator, Page, expect } from '@playwright/test';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

/**
 * Layout coverage for the server-side-rendered problem statement. Neither Vitest nor the parity harness can see
 * layout: jsdom performs no layout at all, so the only way to prove the panel still behaves is in a real browser.
 *
 * Scope note: the student code editor is NOT covered here. `code-editor-student-container.component.ts:102` sets
 * `lightweight` to `!exercise?.exerciseGroup`, so for a course exercise the instructions panel renders with `d-none`
 * and is never visible. It only shows for exam exercises, which `ProblemStatementRendererComponent.serverRendered`
 * keeps on the legacy renderer whatever the toggle says, so there is no SSR rendering to assert there. The remaining
 * editor host is the LocalVC repository view, which needs a prepared participation repository.
 */
// `@sequential`, not `@fast`: the feature toggle below is global server state, and `fast-tests` is fully parallel
// across files. Serial mode alone only orders the tests inside this describe; a concurrent programming suite in
// another worker would still load whichever renderer this file happens to have switched on. The `sequential-tests`
// project runs in its own single-worker invocation after the parallel ones (see `run-tests.sh`).
test.describe('SSR problem statement layout', { tag: '@sequential' }, () => {
    test.describe.configure({ mode: 'serial' });

    let programmingExercise: any;

    // Long enough that the statement would overflow a bounded container. Without the length the "no nested scrollbar"
    // assertion below would pass vacuously against content that simply fits.
    const longProblemStatement = [
        '# Long statement',
        '',
        '1. [task][doOverlap](testDoOverlapObviousPair(),testDoOverlapAdjacentIsNotOverlap())',
        '2. [task][isValidSelection](testIsValidSelectionEmpty())',
        '',
        ...Array.from({ length: 60 }, (_, index) => `Filler paragraph ${index + 1}. ` + 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. '.repeat(4)),
    ].join('\n\n');

    async function setSsrToggle(page: Page, active: boolean): Promise<void> {
        const response = await page.request.put('api/admin/feature-toggle', { data: { SsrProblemStatement: active } });
        expect(response.ok(), `toggling SsrProblemStatement to ${active} failed with ${response.status()}`).toBeTruthy();
    }

    /** Returns the element's own scroll metrics so a caller can assert whether it overflows. */
    async function scrollMetrics(locator: Locator): Promise<{ scrollHeight: number; clientHeight: number }> {
        return await locator.evaluate((element) => ({ scrollHeight: element.scrollHeight, clientHeight: element.clientHeight }));
    }

    test.beforeEach('Create programming exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        programmingExercise = await exerciseAPIRequests.createProgrammingExercise({ course, problemStatement: longProblemStatement });
    });

    test.afterEach('Reset the toggle', async ({ login, page }) => {
        await login(admin);
        await setSsrToggle(page, false);
    });

    test('toggle OFF: the exercise details page renders the legacy pipeline unchanged', async ({ login, page }) => {
        await login(admin);
        await setSsrToggle(page, false);

        await login(studentOne, `/courses/${course.id}/exercises/${programmingExercise.id}`);

        const legacy = page.locator('jhi-programming-exercise-instructions');
        await expect(legacy).toBeVisible({ timeout: 60_000 });
        await expect(page.locator('jhi-programming-exercise-instruction-ssr')).toHaveCount(0);

        // The wrapper inserted by this branch must not collapse the panel it now surrounds.
        const wrapper = page.locator('jhi-problem-statement-renderer');
        await expect(wrapper).toBeVisible();
        expect((await wrapper.boundingBox())!.height).toBeGreaterThan(100);
    });

    test('toggle ON: the statement renders and the page scroller stays the scroller', async ({ login, page }) => {
        await login(admin);
        await setSsrToggle(page, true);

        await login(studentOne, `/courses/${course.id}/exercises/${programmingExercise.id}`);

        const ssr = page.locator('jhi-programming-exercise-instruction-ssr');
        await expect(ssr).toBeVisible({ timeout: 60_000 });
        await expect(page.locator('jhi-programming-exercise-instructions')).toHaveCount(0);

        const scrollArea = ssr.locator('.ssr-scroll-area');
        await expect(scrollArea).toBeVisible();

        // Wait for the rendered HTML to arrive, otherwise the area is empty and every measurement below is vacuous.
        await expect(async () => {
            expect((await scrollMetrics(scrollArea)).scrollHeight).toBeGreaterThan(200);
        }).toPass({ timeout: 30_000 });

        // Outside the code editor there is no bounded ancestor, so `height: 100%` falls back to `auto`, the column
        // sizes to its content, and this element must not become a scroller of its own.
        const metrics = await scrollMetrics(scrollArea);
        expect(metrics.scrollHeight, 'the SSR scroll area became a nested scroller outside the code editor').toBeLessThanOrEqual(metrics.clientHeight + 4);

        // The step wizard is chrome and must render outside the scrolling box.
        const wizardInsideScrollArea = await scrollArea.locator('jhi-programming-exercise-instruction-ssr-step-wizard').count();
        expect(wizardInsideScrollArea, 'the step wizard is inside the scroll area and would scroll away').toBe(0);
    });

    test('toggle ON: the frame restates the application typography', async ({ login, page, exerciseAPIRequests }) => {
        // Read inside the frame, because that is the only place the defect was visible. The frame is a separate
        // document with an opaque origin: no application stylesheet and no theme variable reaches it, so every size
        // has to be restated in embedded.css. When it declared none, headings fell back to the browser defaults and
        // the statement rendered at a visibly different scale from the rest of the page. No server-side assertion can
        // see that, and the parity gate renders with includeCss=false and diffs markup, so the computed values are
        // the only real guard.
        await login(admin);
        const headingExercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            problemStatement: ['# H1', '## H2', '### H3', '#### H4', '##### H5', '###### H6', '', 'Body text.'].join('\n\n'),
        });
        await setSsrToggle(page, true);

        await login(studentOne, `/courses/${course.id}/exercises/${headingExercise.id}`);

        const frame = page.locator('jhi-programming-exercise-instruction-ssr-content iframe');
        await expect(frame).toBeVisible({ timeout: 60_000 });

        const statement = page.frameLocator('jhi-programming-exercise-instruction-ssr-content iframe').locator('.artemis-problem-statement');
        await expect(statement).toBeVisible({ timeout: 30_000 });

        const typography = await statement.evaluate((root) => {
            const of = (selector: string) => {
                const element = root.querySelector(selector);
                const style = getComputedStyle(element!);
                // The line height is compared as a ratio rather than in pixels: the declaration is unitless, so a
                // pixel expectation would have to be restated per heading and would drift on any size change.
                return {
                    size: style.fontSize,
                    weight: style.fontWeight,
                    lineHeight: Math.round((parseFloat(style.lineHeight) / parseFloat(style.fontSize)) * 100) / 100,
                    marginTop: style.marginTop,
                    marginBottom: style.marginBottom,
                };
            };
            return { body: getComputedStyle(root).fontSize, h1: of('h1'), h2: of('h2'), h3: of('h3'), h4: of('h4'), h5: of('h5'), h6: of('h6') };
        });

        // The application's own values against the browser's 16px root: $font-size-base 0.9rem for body text, the
        // `.markdown-preview` scale for h1-h3, Bootstrap's scale on that base for h4-h6. h4 is larger than h1 and h5
        // equal to it because `.markdown-preview` overrides only the first three; that is reproduced on purpose.
        expect(typography.body, 'body text no longer matches $font-size-base').toBe('14.4px');
        // $headings-line-height 1.2 and $headings-margin-bottom 0.5rem come from Bootstrap's reboot, which the frame
        // does not get either, so they are part of the same contract as the sizes and are asserted with them.
        const box = { lineHeight: 1.2, marginTop: '0px', marginBottom: '8px' };
        expect(typography.h1).toEqual({ size: '18px', weight: '400', ...box });
        expect(typography.h2).toEqual({ size: '16.8px', weight: '400', ...box });
        expect(typography.h3).toEqual({ size: '15.6px', weight: '400', ...box });
        expect(typography.h4).toEqual({ size: '21.6px', weight: '400', ...box });
        // global.scss overrides h1-h4 to 400 under "Bootstrap tweaks"; h5 and h6 keep $headings-font-weight.
        expect(typography.h5).toEqual({ size: '18px', weight: '500', ...box });
        expect(typography.h6).toEqual({ size: '14.4px', weight: '500', ...box });
    });

    test('toggle ON: the statement is isolated in a sandboxed frame', async ({ login, page }) => {
        // Asserted against the frame the application actually built, not one the test assembled. That distinction
        // is the point: bound as `[attr.srcdoc]`, Angular's sanitizer silently reduced the document to a few
        // characters, dropping the policy and the script, while every unit assertion about the string still
        // passed. Only reading the element back catches a regression of that shape.
        await login(admin);
        await setSsrToggle(page, true);

        await login(studentOne, `/courses/${course.id}/exercises/${programmingExercise.id}`);

        const frame = page.locator('jhi-programming-exercise-instruction-ssr-content iframe');
        await expect(frame).toBeVisible({ timeout: 60_000 });

        // No allow-same-origin: this single attribute is what denies the statement the cookies, the storage and
        // the parent DOM.
        expect(await frame.getAttribute('sandbox')).toBe('allow-scripts');

        const document_ = await frame.evaluate((element) => (element as HTMLIFrameElement).srcdoc);
        const nonce = /<script nonce="([0-9a-f]{32})">/.exec(document_)?.[1];

        expect(nonce, 'the frame carries no nonced script, so it cannot report its height either').toBeDefined();
        expect(document_).toContain(`script-src 'nonce-${nonce}'`);
        expect(document_).toContain("default-src 'none'");
        expect(document_).toContain("connect-src 'none'");
        // The statement itself made it in, so the assertions above are about a real document.
        expect(document_).toContain('artemis-problem-statement');

        // And the frame really did size itself, which only happens if its script ran.
        await expect(async () => {
            expect((await frame.boundingBox())!.height).toBeGreaterThan(200);
        }).toPass({ timeout: 30_000 });
    });
});
