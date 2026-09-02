import dayjs from 'dayjs';
import { Page, expect } from '@playwright/test';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';

import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { Commands } from '../../../support/commands';
import { ExerciseAPIRequests } from '../../../support/requests/ExerciseAPIRequests';
import { SEED_COURSES } from '../../../support/seedData';
import { dismissPasskeyReminderIfPresent } from '../../../support/dismissPasskeyReminder';
import { expectNoScrollPastApollonCanvas, newBrowserPage } from '../../../support/utils';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

/**
 * A page that scrolls past an Apollon canvas is broken: the canvas eats the wheel, so whatever sits
 * below it cannot be reached. These tests pin the boundary — the working surfaces must never scroll,
 * and the one page that may (the exercise form) must hand the wheel back via Apollon's scroll lock.
 *
 * The assertions measure the editor's ancestors, since it is content above or below the editor that
 * pushes a page over the edge.
 */
test.describe('Apollon canvas is never scrolled past', { tag: '@fast' }, () => {
    let modelingExercise: ModelingExercise;

    test.beforeEach('Create modeling exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
    });

    for (const viewport of [
        { width: 1440, height: 960 },
        // Short viewports are where the margin disappears first.
        { width: 1280, height: 720 },
    ]) {
        test(`the student participation view fits its frame at ${viewport.width}x${viewport.height}`, async ({ login, page, courseOverview }) => {
            await page.setViewportSize(viewport);
            await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
            await courseOverview.startExercise(modelingExercise.id!);

            await expectNoScrollPastApollonCanvas(page);
        });
    }

    test('the exercise form scrolls, and gives the wheel back through scroll lock', async ({ login, page }) => {
        await login(admin, `/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/edit`);
        await expect(page.locator('.apollon-editor')).toBeVisible();

        const pageScrolls = await page.evaluate(() => document.documentElement.scrollHeight > document.documentElement.clientHeight + 1);
        const containerScrolls = await page.locator('#course-body-container').evaluate((element) => element.scrollHeight > element.clientHeight + 1);
        expect(pageScrolls || containerScrolls).toBe(true);

        // With scroll lock engaged, a wheel over the canvas must scroll the page instead of zooming.
        const scroller = page.locator('#course-body-container');
        const before = await scroller.evaluate((element) => element.scrollTop);
        const zoom = page.locator('[data-apollon-control="apollon:zoom"]');
        const zoomBefore = (await zoom.textContent()) ?? '';

        await page.locator('.apollon-editor').hover();
        await page.mouse.wheel(0, 400);

        await expect.poll(() => scroller.evaluate((element) => element.scrollTop)).toBeGreaterThan(before);
        await expect(zoom).toHaveText(zoomBefore);
    });
});

/**
 * The Athena notice is chrome, not a band: an island floating in Apollon's top-left corner. Showing
 * it must therefore change neither the canvas' size nor the page's scroll height.
 */
test.describe('Athena chrome on the tutor assessment page', { tag: '@fast' }, () => {
    const assessmentCourse = { id: SEED_COURSES.exerciseAssessment.id } as any;
    let assessmentExercise: ModelingExercise;

    // The E2E seed provisions no exercises, so the submission this assessment page needs is created here and reached
    // through the dashboard rather than through a hand-built URL with guessed ids.
    test.beforeAll('Create a modeling exercise with a submission ready to assess', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);

        await Commands.login(page, admin);
        assessmentExercise = await exerciseAPIRequests.createModelingExercise({ course: assessmentCourse });
        await Commands.login(page, studentOne);
        const participation = await (await exerciseAPIRequests.startExerciseParticipation(assessmentExercise.id!)).json();
        await exerciseAPIRequests.makeModelingExerciseSubmission(assessmentExercise.id!, participation);
        await Commands.login(page, instructor);
        await exerciseAPIRequests.updateModelingExerciseDueDate(assessmentExercise, dayjs());
    });

    /**
     * Athena is not part of the E2E stack, so the notice is driven the way the
     * page actually reads it: an AUTOMATIC feedback on a result the logged-in
     * tutor owns and has not submitted yet.
     */
    async function showAutomaticAssessmentNotice(page: Page) {
        const account = await page.request.get('api/core/public/account');
        const userId = (await account.json()).id;

        await page.route('**/api/modeling/modeling-submissions/*', async (route) => {
            const response = await route.fetch();
            const body = await response.json();
            const result = body?.results?.[body.results.length - 1];
            if (result) {
                delete result.completionDate;
                result.assessor = { id: userId };
                result.feedbacks = [...(result.feedbacks ?? []), { type: 'AUTOMATIC', credits: 0, text: 'automatic', detailText: 'automatic', positive: true }];
            }
            await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
        });
    }

    const canvasSize = async (page: Page) => {
        const box = (await page.locator('jhi-modeling-assessment .apollon-editor').boundingBox())!;
        return { width: Math.round(box.width), height: Math.round(box.height) };
    };

    /** Apollon lays the canvas out around its chrome, so a control being on screen is what makes a measurement final. */
    const canvasIsLaidOut = (page: Page) => expect(page.locator('jhi-modeling-assessment [data-apollon-control="apollon:zoom"]')).toBeVisible();

    const waitForLayout = (page: Page) => page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));

    test('shows loaded automatic feedback in the legend without changing the canvas size', async ({ login, page, exerciseAssessment }) => {
        await page.setViewportSize({ width: 1440, height: 900 });
        // The tutor, not the instructor: this is the tutor assessment page, and the notice is gated on the logged-in
        // user owning the (not yet submitted) assessment.
        await login(tutor, `/course-management/${assessmentCourse.id}/assessment-dashboard/${assessmentExercise.id!}`);
        await dismissPasskeyReminderIfPresent(page);
        await exerciseAssessment.clickHaveReadInstructionsButton();
        await exerciseAssessment.clickStartNewAssessment();
        const automaticFeedbackLegendItem = page.locator('jhi-modeling-assessment-legend .assessment-legend__item').filter({ hasText: /Automatic assessment/i });
        await expect(automaticFeedbackLegendItem).toHaveCount(0);
        await canvasIsLaidOut(page);
        await waitForLayout(page);
        const without = await canvasSize(page);
        await expectNoScrollPastApollonCanvas(page);

        await showAutomaticAssessmentNotice(page);
        await page.reload({ waitUntil: 'domcontentloaded' });
        await expect(automaticFeedbackLegendItem).toBeVisible();
        await expect(page.locator('jhi-modeling-assessment [data-apollon-region="top-right"] jhi-modeling-assessment-legend')).toBeVisible();
        await expect(page.locator('jhi-modeling-assessment .feedback-suggestions-chrome')).toHaveCount(0);

        await canvasIsLaidOut(page);
        await waitForLayout(page);
        await expect.poll(() => canvasSize(page)).toEqual(without);
        await expectNoScrollPastApollonCanvas(page);
    });
});
