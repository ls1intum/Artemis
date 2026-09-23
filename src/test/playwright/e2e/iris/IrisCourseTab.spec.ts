import { Page, expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { Course } from 'app/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { generateUUID } from '../../support/utils';

/**
 * Gets a student past the one-time overlays Iris puts in front of the chat: the LLM usage choice and the onboarding
 * tour. Both are remembered per user, so whether they appear depends on what an earlier run already answered, and both
 * cover the page with a backdrop that swallows every other click while they are up.
 */
async function openIrisChat(page: Page): Promise<void> {
    await expect(page.locator('jhi-course-chatbot')).toBeVisible({ timeout: 30_000 });

    const cloudOption = page.locator('.option-card.cloud-card');
    const messageInput = page.locator('.chat-input textarea');
    await expect(cloudOption.or(messageInput).first()).toBeVisible({ timeout: 30_000 });
    if (await cloudOption.isVisible()) {
        await cloudOption.click();
        await expect(cloudOption).toBeHidden();
    }

    // The tour is offered a moment after the chat settles, so give it time to appear before deciding it will not
    await page.waitForTimeout(2000);
    const tourClose = page.locator('.iris-onboarding-modal-welcome .close-button, .onboarding-tooltip .tooltip-close-button').first();
    if (await tourClose.isVisible().catch(() => false)) {
        await tourClose.click();
    }
    await expect(page.locator('.onboarding-container .full-backdrop')).toHaveCount(0);
    await expect(page.locator('.onboarding-container .spotlight-click-guard')).toHaveCount(0);
    await expect(messageInput).toBeVisible();
}

/**
 * Coverage for the Iris tab of the course overview and, above all, for its context picker.
 *
 * The picker used to read its lectures and exercises off the course object that the overview happened to have fully
 * loaded. Now that the overview loads each tab's content separately, that object no longer carries them, so the picker
 * loads its options from the server when it is first opened. This suite pins that: the options must appear even though
 * nothing on the Iris tab loads course content, and they must be requested lazily rather than on every page view.
 *
 * Requires Iris to be enabled on the server; the suite skips itself otherwise. Run with:
 *     RUN_IRIS=true ./run-e2e-tests-local-fast.sh --skip-db --filter "Iris"
 *
 * Tagged `@slow` like the other Iris suites: the tab boots the whole chat stack on a cold route.
 */
test.describe('Iris course tab (real Pyris)', { tag: '@slow' }, () => {
    let course: Course;
    let lecture: Lecture;
    let exercise: TextExercise;

    test.beforeAll(async ({ browser }) => {
        const probeContext = await browser.newContext();
        const info = await probeContext.request.get('management/info');
        const features: string[] = info.ok() ? ((await info.json())?.activeModuleFeatures ?? []) : [];
        await probeContext.close();
        test.skip(!features.includes('iris'), 'Iris module feature is not active on the server (run with RUN_IRIS=true)');
    });

    test.beforeEach('Create a course with Iris enabled, one lecture and one exercise', async ({ login, page, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        const settingsResponse = await page.request.put(`api/iris/courses/${course.id}/iris-settings`, { data: { enabled: true, variant: 'default' } });
        expect(settingsResponse.ok(), 'Iris must be enabled for the course, otherwise the tab is not offered').toBeTruthy();

        lecture = await courseManagementAPIRequests.createLecture(course, 'IrisLec ' + generateUUID());
        const released = dayjs().subtract(2, 'day');
        const due = dayjs().add(2, 'day');
        exercise = await exerciseAPIRequests.createTextExerciseWithDates({ course }, released, due, due.add(1, 'hour'), 'IrisEx ' + generateUUID());
    });

    test.afterEach('Delete the course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('offers the course lectures and exercises in the context picker, loaded only when it is opened', async ({ page, login }) => {
        const lectureRequests: string[] = [];
        const exerciseTitleRequests: string[] = [];
        page.on('request', (request) => {
            if (request.url().includes(`courses/${course.id}/lectures-for-overview`)) {
                lectureRequests.push(request.url());
            }
            if (request.url().includes(`courses/${course.id}/exercise-titles`)) {
                exerciseTitleRequests.push(request.url());
            }
        });

        await login(studentOne, `/courses/${course.id}/iris`);
        await openIrisChat(page);

        const picker = page.locator('jhi-context-selection');
        await expect(picker).toBeVisible();

        // The Iris tab renders no course content, so nothing may have fetched the options yet
        expect(lectureRequests, 'the picker must not load its lectures before it is opened').toHaveLength(0);
        expect(exerciseTitleRequests, 'the picker must not load its exercises before it is opened').toHaveLength(0);

        await picker.locator('.iris-context-trigger').click();

        // Both come from the server, not from a course object the overview happened to have loaded
        await expect.poll(() => lectureRequests.length, { timeout: 15_000 }).toBe(1);
        await expect.poll(() => exerciseTitleRequests.length, { timeout: 15_000 }).toBe(1);

        const options = page.getByTestId('iris-context-overlay').getByTestId('iris-context-option');
        await expect(options.filter({ hasText: lecture.title! })).toBeVisible({ timeout: 15_000 });
        await expect(options.filter({ hasText: exercise.title! })).toBeVisible();

        // Selecting an exercise sets it as the chat context, which the chip reports back to the student
        await options.filter({ hasText: exercise.title! }).click();
        const chip = picker.locator('.iris-context-chip');
        await expect(chip).toBeVisible();
        await expect(chip).toContainText(exercise.title!);

        // Opening the picker a second time must reuse what it already has rather than asking again
        await picker.locator('.iris-context-trigger').click();
        await expect(options.filter({ hasText: lecture.title! })).toBeVisible();
        expect(lectureRequests, 'the options are loaded once per course, not on every open').toHaveLength(1);
        expect(exerciseTitleRequests, 'the options are loaded once per course, not on every open').toHaveLength(1);
    });

    test('answers a course chat message sent from the tab', async ({ page, login }) => {
        await login(studentOne, `/courses/${course.id}/iris`);
        await openIrisChat(page);

        const input = page.locator('.chat-input textarea');
        await input.fill('Hello Iris, what is this course about?');
        await page.locator('#irisSendButton').click();

        // The mock LLM the Iris stack runs against always includes this marker in its reply
        await expect(page.locator('.llm-message-wrapper').last()).toContainText('mock-llm', { timeout: 60_000 });
    });
});
