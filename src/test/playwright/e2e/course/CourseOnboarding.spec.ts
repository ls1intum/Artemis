import { test } from '../../support/fixtures';
import { Course } from 'app/core/course/shared/entities/course.model';
import { admin } from '../../support/users';
import { expect } from '@playwright/test';
import { generateUUID } from '../../support/utils';
import { BASE_API } from '../../support/constants';

const COURSE_UPDATE_BASE = `${BASE_API}/core/courses`;

test.describe('Course onboarding wizard', { tag: '@fast' }, () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin, '/');
        const uid = generateUUID();
        course = await courseManagementAPIRequests.createCourse({ courseName: 'Onboarding ' + uid, courseShortName: 'pw' + uid });
    });

    test('Auto-redirects to onboarding for new course', async ({ page }) => {
        await page.goto(`/course-management/${course.id}`);
        await page.waitForURL(`**/course-management/${course.id}/onboarding`);
        await expect(page.locator('.onboarding-wizard')).toBeVisible();
    });

    test('Walks through all wizard steps and finishes setup', async ({ page, courseOnboarding }) => {
        await page.goto(`/course-management/${course.id}/onboarding`);
        await courseOnboarding.expectWizardVisible();

        // Step 0: General Settings
        await expect(courseOnboarding.getActiveStepItem()).toBeVisible();
        await courseOnboarding.expectNoPreviousButton();
        await courseOnboarding.expectNextButtonVisible();

        // Advance to step 1: Enrollment
        const nextResponse1 = page.waitForResponse((resp) => resp.url().includes(`${COURSE_UPDATE_BASE}/${course.id}`) && resp.request().method() === 'PUT');
        await courseOnboarding.clickNext();
        await nextResponse1;
        await expect(page.locator('#onboarding_enrollmentEnabled')).toBeVisible();
        await courseOnboarding.expectPreviousButtonVisible();

        // Advance to step 2: Communication
        const nextResponse2 = page.waitForResponse((resp) => resp.url().includes(`${COURSE_UPDATE_BASE}/${course.id}`) && resp.request().method() === 'PUT');
        await courseOnboarding.clickNext();
        await nextResponse2;
        await expect(page.locator('#onboarding_communicationEnabled')).toBeVisible();

        // Skip step 3: Assessment & AI
        await courseOnboarding.clickSkip();
        await expect(page.locator('#onboarding_complaintsEnabled')).toBeVisible();

        // Skip to step 4: Explore
        await courseOnboarding.clickSkip();
        await courseOnboarding.expectExploreCardsVisible();
        await courseOnboarding.expectFinishButtonVisible();

        // Finish setup
        const finishResponse = page.waitForResponse((resp) => resp.url().includes(`${COURSE_UPDATE_BASE}/${course.id}`) && resp.request().method() === 'PUT');
        await courseOnboarding.clickFinishSetup();
        await finishResponse;

        // Should redirect to course management page (not back to onboarding)
        await page.waitForURL(`**/course-management/${course.id}`);
        await expect(page.locator('.onboarding-wizard')).toBeHidden();
    });

    test('Previous button navigates back', async ({ page, courseOnboarding }) => {
        await page.goto(`/course-management/${course.id}/onboarding`);
        await courseOnboarding.expectWizardVisible();

        // Go to step 1
        const nextResponse = page.waitForResponse((resp) => resp.url().includes(`${COURSE_UPDATE_BASE}/${course.id}`) && resp.request().method() === 'PUT');
        await courseOnboarding.clickNext();
        await nextResponse;
        await expect(page.locator('#onboarding_enrollmentEnabled')).toBeVisible();

        // Go back to step 0
        await courseOnboarding.clickPrevious();
        await courseOnboarding.expectNoPreviousButton();
    });

    test('Replay wizard from course settings', async ({ page, courseManagement }) => {
        // First, complete the onboarding
        await page.goto(`/course-management/${course.id}/onboarding`);
        await page.locator('.onboarding-wizard').waitFor({ state: 'visible' });

        // Skip through all steps and finish
        for (let i = 0; i < 4; i++) {
            await page.locator('.footer-right .btn-outline-secondary').click();
        }
        const finishResponse = page.waitForResponse((resp) => resp.url().includes(`${COURSE_UPDATE_BASE}/${course.id}`) && resp.request().method() === 'PUT');
        await page.locator('.footer-right .btn-success').click();
        await finishResponse;
        await page.waitForURL(`**/course-management/${course.id}`);

        // Now navigate to course settings and find the replay button
        await courseManagement.openCourseSettings();
        const replayLink = page.locator('a[href*="onboarding"]');
        await expect(replayLink).toBeVisible();
        await replayLink.click();

        // Should open the onboarding wizard again
        await page.waitForURL(`**/course-management/${course.id}/onboarding`);
        await expect(page.locator('.onboarding-wizard')).toBeVisible();
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
