import { Locator, Page, expect } from '@playwright/test';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

/**
 * Layout coverage for the server-side-rendered problem statement. Neither Vitest nor the parity harness can see
 * layout: jsdom performs no layout at all, so the only way to prove the panel still behaves is in a real browser.
 *
 * Scope note: the student code editor is NOT covered here. `code-editor-student-container.component.ts:101` sets
 * `lightweight` to `!exercise?.exerciseGroup`, so for a course exercise the instructions panel renders with `d-none`
 * and is never visible. It only shows for exam exercises, which this migration deliberately excludes. The remaining
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
});
