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

    test('toggle ON: the statement restates the application typography', async ({ login, page, exerciseAPIRequests }) => {
        // Read inside the shadow root, which is the only place this is visible. The statement lives in a shadow root
        // that no application stylesheet crosses, so every size has to be restated in embedded.css, and a missing
        // declaration falls back to the browser default and renders the statement at a visibly different scale from
        // the rest of the page. No server-side assertion can see that, and the parity gate renders with
        // includeCss=false and diffs markup, so the computed values are the only guard.
        await login(admin);
        const headingExercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            problemStatement: ['# H1', '## H2', '### H3', '#### H4', '##### H5', '###### H6', '', 'Body text.'].join('\n\n'),
        });
        await setSsrToggle(page, true);

        await login(studentOne, `/courses/${course.id}/exercises/${headingExercise.id}`);

        // Playwright locators pierce the open shadow root, so the statement is reachable directly.
        const statement = page.locator('jhi-programming-exercise-instruction-ssr-content').locator('.artemis-problem-statement');
        await expect(statement).toBeVisible({ timeout: 60_000 });

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

    test('toggle ON: formulas and code are rendered and coloured inside the shadow root', async ({ login, page, exerciseAPIRequests }) => {
        // Formulas are server-generated native MathML the browser lays out itself; highlight.js runs in the client. Neither
        // the MathML layout nor the highlight palette can be seen server-side or in jsdom, so a real browser reading the
        // shadow root is the only guard that both arrived.
        await login(admin);
        const richExercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            problemStatement: [
                '# Rich',
                '',
                'Inline $O(n \\log n)$ and display:',
                '',
                '$$\\sum_{k=1}^{n} k^2$$',
                '',
                '```java',
                'public int add(int a, int b) { return a + b; }',
                '```',
            ].join('\n'),
        });
        await setSsrToggle(page, true);

        await login(studentOne, `/courses/${course.id}/exercises/${richExercise.id}`);

        const host = page.locator('jhi-programming-exercise-instruction-ssr-content');
        await expect(host).toBeVisible({ timeout: 60_000 });

        // Playwright locators pierce the open shadow root. Native MathML renders as <math> elements.
        await expect(host.locator('math').first()).toBeVisible({ timeout: 30_000 });
        expect(await host.locator('math').count(), 'both the inline and the display formula rendered').toBeGreaterThanOrEqual(2);
        await expect(host.locator('math[display="block"]').first(), 'the display formula carries display=block').toBeVisible();

        const code = host.locator('pre code.hljs');
        await expect(code).toBeVisible();
        // The highlight.js palette lives in the server's embedded.css, which is injected into the shadow root. If it
        // did not reach the shadow tree the token would fall back to the statement's plain text colour.
        const keywordColor = await host
            .locator('pre code.hljs .hljs-keyword')
            .first()
            .evaluate((element) => getComputedStyle(element).color);
        const textColor = await host.locator('.artemis-problem-statement').evaluate((element) => getComputedStyle(element).color);
        expect(keywordColor, 'the highlight.js palette did not reach the shadow root').not.toBe(textColor);
    });
});
