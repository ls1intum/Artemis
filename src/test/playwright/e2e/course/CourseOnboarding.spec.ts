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

    test('Admin is NOT auto-redirected to onboarding', async ({ page }) => {
        await page.goto(`/course-management/${course.id}`);
        // Admin users should stay on the course detail page (no redirect)
        await expect(page.locator('.onboarding-wizard')).toBeHidden();
        // But admin can still access onboarding manually
        await page.goto(`/course-management/${course.id}/onboarding`);
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

        // Advance to step 3: Assessment & AI
        const nextResponse3 = page.waitForResponse((resp) => resp.url().includes(`${COURSE_UPDATE_BASE}/${course.id}`) && resp.request().method() === 'PUT');
        await courseOnboarding.clickNext();
        await nextResponse3;
        await expect(page.locator('#onboarding_complaintsEnabled')).toBeVisible();
        await courseOnboarding.expectFinishButtonVisible();

        // Finish setup (saves onboardingDone=true and advances to step 4: Explore)
        const finishResponse = page.waitForResponse((resp) => resp.url().includes(`${COURSE_UPDATE_BASE}/${course.id}`) && resp.request().method() === 'PUT');
        await courseOnboarding.clickFinishSetup();
        await finishResponse;
        await courseOnboarding.expectExploreCardsVisible();

        // Go to course overview
        await courseOnboarding.clickGoToCourse();
        await page.waitForURL(`**/course-management/${course.id}**`);
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

    test('Replay wizard from course overview', async ({ page, courseOnboarding }) => {
        await page.goto(`/course-management/${course.id}/onboarding`);
        await courseOnboarding.expectWizardVisible();

        // Walk through steps 0→1→2→3 using Next
        for (let i = 0; i < 3; i++) {
            const resp = page.waitForResponse((r) => r.url().includes(`${COURSE_UPDATE_BASE}/${course.id}`) && r.request().method() === 'PUT');
            await courseOnboarding.clickNext();
            await resp;
        }

        // Finish setup on step 3 (advances to step 4)
        const finishResponse = page.waitForResponse((resp) => resp.url().includes(`${COURSE_UPDATE_BASE}/${course.id}`) && resp.request().method() === 'PUT');
        await courseOnboarding.clickFinishSetup();
        await finishResponse;

        // Go to course overview from step 4
        await courseOnboarding.clickGoToCourse();
        await page.waitForURL(`**/course-management/${course.id}**`);

        // Find and click the "Course Onboarding" link to replay the wizard
        const replayButton = page.locator('a', { hasText: 'Course Onboarding' });
        await expect(replayButton).toBeVisible();
        await replayButton.click();

        // Should open the onboarding wizard again
        await page.waitForURL(`**/course-management/${course.id}/onboarding`);
        await expect(page.locator('.onboarding-wizard')).toBeVisible();
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
