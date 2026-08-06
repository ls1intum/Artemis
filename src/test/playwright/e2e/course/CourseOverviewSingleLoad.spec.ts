import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';

/**
 * Regression test for issue #12905: navigating into a course from the course overview must issue exactly one
 * courses/{id}/for-dashboard request. A redundant routerLink on the course card previously produced two navigations
 * to the same URL (with onSameUrlNavigation: 'reload'), which canceled the first navigation's guard fetch and
 * re-issued it, so the expensive for-dashboard endpoint was hit twice (one canceled, one 200) on every course entry.
 */
test.describe('Course overview navigation', { tag: '@fast' }, () => {
    test('sends exactly one for-dashboard request when entering a course', async ({ page, login, courseManagementAPIRequests }) => {
        // Create a course and enroll the student. Entering the course redirects to the exercises tab, and the
        // course container issues a single for-dashboard request to load the course, exactly the flow that
        // triggered the duplicate request.
        await login(admin);
        const course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);

        // Count only the single-course call; the all-courses courses/for-dashboard (overview list) has a different URL.
        const singleCourseForDashboard = `courses/${course.id}/for-dashboard`;
        const requests: string[] = [];
        const canceled: string[] = [];
        page.on('request', (request) => {
            if (request.url().includes(singleCourseForDashboard)) {
                requests.push(request.url());
            }
        });
        page.on('requestfailed', (request) => {
            if (request.url().includes(singleCourseForDashboard)) {
                canceled.push(request.failure()?.errorText ?? 'failed');
            }
        });

        await login(studentOne, '/courses');

        // Enter the course via the card (SPA navigation), mirroring how a student opens a course.
        const courseCard = page.locator(`#course-${course.id}-header`);
        await courseCard.waitFor({ state: 'visible' });
        await courseCard.click();
        await page.waitForURL(`**/courses/${course.id}/**`);
        // Give any (erroneous) duplicate/canceled request time to appear before asserting.
        await page.waitForTimeout(1500);

        expect(requests, `for-dashboard requests: ${JSON.stringify(requests)}`).toHaveLength(1);
        expect(canceled, `canceled for-dashboard requests: ${JSON.stringify(canceled)}`).toHaveLength(0);
    });
});
