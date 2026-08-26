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

        // This page IS a form: grading, dates and the footer live below the
        // editor, so scrolling is the point rather than the bug.
        const pageScrolls = await page.evaluate(() => document.documentElement.scrollHeight > document.documentElement.clientHeight + 1);
        const containerScrolls = await page.locator('#course-body-container').evaluate((element) => element.scrollHeight > element.clientHeight + 1);
        expect(pageScrolls || containerScrolls).toBe(true);

        // Which is why the wheel must reach the page: with scroll lock engaged, a wheel over the
        // canvas scrolls past it instead of zooming. The editor starts below the fold here, so it
        // has to be brought into view before the pointer can land on it.
        const canvas = page.locator('.apollon-editor');
        await canvas.scrollIntoViewIfNeeded();
        const box = await canvas.boundingBox();
        const scroller = page.locator('#course-body-container');
        const before = await scroller.evaluate((element) => element.scrollTop);
        const zoom = page.locator('[data-apollon-control="apollon:zoom"]');
        const zoomBefore = await zoom.textContent();

        await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
        await page.mouse.wheel(0, 400);

        await expect.poll(() => scroller.evaluate((element) => element.scrollTop)).toBeGreaterThan(before);
        // ...and it scrolls rather than zooming, which is the whole point of the lock.
        expect(await zoom.textContent()).toBe(zoomBefore);
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

    async function canvasBox(page: Page) {
        const canvas = page.locator('jhi-modeling-assessment .apollon-editor');
        await expect(canvas).toBeVisible();
        // The chrome mounts a frame after Apollon settles, so measure only once the box has stopped moving.
        let previous: string | undefined;
        await expect
            .poll(
                async () => {
                    const current = JSON.stringify(await canvas.boundingBox());
                    const settled = current === previous;
                    previous = current;
                    return settled;
                },
                { intervals: Array(12).fill(250) },
            )
            .toBe(true);
        return (await canvas.boundingBox())!;
    }

    test('floats the notice over the canvas instead of taking height from it', async ({ login, page, exerciseAssessment }) => {
        await page.setViewportSize({ width: 1440, height: 900 });
        // The tutor, not the instructor: this is the tutor assessment page, and the notice is gated on the logged-in
        // user owning the (not yet submitted) assessment.
        await login(tutor, `/course-management/${assessmentCourse.id}/assessment-dashboard/${assessmentExercise.id!}`);
        await dismissPasskeyReminderIfPresent(page);
        await exerciseAssessment.clickHaveReadInstructionsButton();
        await exerciseAssessment.clickStartNewAssessment();
        const island = page.locator('jhi-modeling-assessment .feedback-suggestions-chrome');
        // Baseline: no notice on the seeded submission, so the comparison below is
        // a real before/after rather than two identical states.
        await expect(island).toHaveCount(0);
        // ...and an unoccupied slot must leave the region unmounted: an empty region
        // still reserves an inset through `getRegionElement`.
        await expect(page.locator('jhi-modeling-assessment [data-apollon-region="top-left"]')).toHaveCount(0);
        const without = await canvasBox(page);
        await expectNoScrollPastApollonCanvas(page);

        await showAutomaticAssessmentNotice(page);
        await page.reload({ waitUntil: 'domcontentloaded' });
        await expect(island).toBeVisible();

        // Chrome, not a band: inside the editor, in the canvas' top-left corner.
        await expect(page.locator('jhi-modeling-assessment [data-apollon-region="top-left"] .feedback-suggestions-chrome')).toBeVisible();

        const withNotice = await canvasBox(page);
        expect(Math.abs(withNotice.height - without.height)).toBeLessThanOrEqual(1);
        expect(Math.abs(withNotice.width - without.width)).toBeLessThanOrEqual(1);

        // It keeps the same edge gap as Apollon's own islands and clears them all.
        const islandBox = (await island.boundingBox())!;
        expect(Math.round(islandBox.x - withNotice.x)).toBe(Math.round(islandBox.y - withNotice.y));
        for (const control of ['apollon:zoom', 'apollon:minimap']) {
            const box = (await page.locator(`jhi-modeling-assessment [data-apollon-control="${control}"]`).boundingBox())!;
            expect(islandBox.y + islandBox.height, `the notice must not overlap ${control}`).toBeLessThanOrEqual(box.y);
        }

        await expectNoScrollPastApollonCanvas(page);
    });
});
