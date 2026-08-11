import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';

/**
 * Entering a course must load the course shell and its tab availability once each, and must not fall back to the
 * deprecated whole-course endpoint.
 *
 * Originally a regression test for issue #12905, where a redundant routerLink on the course card produced two
 * navigations to the same URL (with onSameUrlNavigation: 'reload'), canceling the first navigation's fetch and
 * re-issuing it. The duplicate-request assertion is kept; what changed is which endpoints a course entry uses.
 */
test.describe('Course overview navigation', { tag: '@fast' }, () => {
    test('loads the course shell and tabs once each, without the deprecated dashboard endpoint', async ({ page, login, courseManagementAPIRequests }) => {
        await login(admin);
        const course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);

        // The all-courses list endpoint is courses/for-dashboard, so match on the single-course URL only
        const forDashboard = `courses/${course.id}/for-dashboard`;
        const forOverview = `courses/${course.id}/for-overview`;
        const availableTabs = `courses/${course.id}/available-tabs`;
        const exercisesForOverview = `courses/${course.id}/exercises-for-overview`;

        const seen: Record<string, string[]> = { forDashboard: [], forOverview: [], availableTabs: [], exercisesForOverview: [] };
        const canceled: string[] = [];
        const track = (url: string, collect: (key: string) => void) => {
            if (url.includes(forDashboard)) collect('forDashboard');
            else if (url.includes(exercisesForOverview)) collect('exercisesForOverview');
            else if (url.includes(forOverview)) collect('forOverview');
            else if (url.includes(availableTabs)) collect('availableTabs');
        };
        page.on('request', (request) => track(request.url(), (key) => seen[key].push(request.url())));
        page.on('requestfailed', (request) => track(request.url(), () => canceled.push(request.failure()?.errorText ?? 'failed')));

        await login(studentOne, '/courses');

        // Enter the course via the card (SPA navigation), mirroring how a student opens a course
        const courseCard = page.locator(`#course-${course.id}-header`);
        await courseCard.waitFor({ state: 'visible' });
        await courseCard.click();
        await page.waitForURL(`**/courses/${course.id}/**`);

        // Wait for the shell to have loaded rather than sleeping a fixed amount, which is what made this flaky under
        // parallel load, then settle briefly so a duplicate would have arrived before the counts are read
        await expect.poll(() => seen.forOverview.length, { timeout: 15_000 }).toBeGreaterThan(0);
        await expect.poll(() => seen.availableTabs.length, { timeout: 15_000 }).toBeGreaterThan(0);
        await page.waitForTimeout(1500);

        expect(seen.forDashboard, `the web client must not use the deprecated endpoint: ${JSON.stringify(seen.forDashboard)}`).toHaveLength(0);
        // The regression this guards is a duplicate request, so the count is the contract. A request the browser aborts
        // mid-redirect is separately recorded and reported here, but only a re-issue shows up as a second entry.
        expect(seen.forOverview, `for-overview requests: ${JSON.stringify(seen.forOverview)}, canceled: ${JSON.stringify(canceled)}`).toHaveLength(1);
        expect(seen.availableTabs, `available-tabs requests: ${JSON.stringify(seen.availableTabs)}, canceled: ${JSON.stringify(canceled)}`).toHaveLength(1);
    });

    test('loads exercises only when the exercises tab is opened, and reloads them when it is selected again', async ({ page, login, courseManagementAPIRequests }) => {
        await login(admin);
        const course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);

        const exercisesForOverview = `courses/${course.id}/exercises-for-overview`;
        const exerciseRequests: string[] = [];
        page.on('request', (request) => {
            if (request.url().includes(exercisesForOverview)) {
                exerciseRequests.push(request.url());
            }
        });

        // Entering the course lands on the exercises tab, which loads its own content
        await login(studentOne, `/courses/${course.id}`);
        await page.waitForURL(`**/courses/${course.id}/exercises**`);
        await expect.poll(() => exerciseRequests.length, { timeout: 15_000 }).toBe(1);

        // Selecting the tab that is already open acts as a refresh, so the data cannot go stale behind the user
        // Scoped to the sidebar: the refresh is driven by the sidebar reporting the click, so any other link to the
        // same URL elsewhere on the page would navigate without being a tab selection
        await page.locator(`jhi-course-sidebar a[href="/courses/${course.id}/exercises"]`).first().click();
        await expect.poll(() => exerciseRequests.length, { timeout: 15_000 }).toBe(2);

        // And it stays at two: the refresh is one request, not a loop
        await page.waitForTimeout(1500);
        expect(exerciseRequests, `after re-selecting the tab: ${JSON.stringify(exerciseRequests)}`).toHaveLength(2);
    });
});
