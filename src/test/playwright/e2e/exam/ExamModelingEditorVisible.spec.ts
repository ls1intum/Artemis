import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { admin, studentOne } from '../../support/users';
import { test } from '../../support/fixtures';
import { SEED_COURSES } from '../../support/seedData';
import { EXAM_GRACE_PERIOD_IN_SECONDS, ExerciseType } from '../../support/constants';
import { expectNoScrollPastApollonCanvas } from '../../support/utils';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

/** Below this the canvas is a strip rather than a working surface. */
const MIN_USABLE_CANVAS_HEIGHT = 400;
/** The canvas is the page's purpose, so it must take most of the room the exercise page offers. */
const MIN_SHARE_OF_HOST = 0.6;
/** Box geometry is rounded per element, so edges never line up exactly. */
const SUB_PIXEL_TOLERANCE = 1;

/**
 * A modeling canvas has no intrinsic height, so a content-sized ancestor collapses it to nothing.
 * The rest of the exam suite drives the editor through its API-shaped page object and would not
 * notice; this asserts the canvas is on screen, fills the space the exercise page gives it, and —
 * the invariant every Apollon surface shares — cannot be scrolled past.
 *
 * Builds the exam inline rather than through `prepareExam`, which hands in and locks the student out.
 */
test.describe('Exam modeling editor', { tag: '@slow' }, () => {
    test('renders a usable canvas that fills the exercise page', async ({ login, page, examAPIRequests, examExerciseGroupCreation, examParticipation, examNavigation }) => {
        await login(admin);
        const end = dayjs().add(1, 'hour');
        const exam = await examAPIRequests.createExam({
            course,
            startDate: dayjs(),
            endDate: end,
            numberOfCorrectionRoundsInExam: 1,
            examStudentReviewStart: end.add(1, 'second'),
            examStudentReviewEnd: end.add(5, 'minutes'),
            publishResultsDate: end.add(1, 'second'),
            gracePeriod: EXAM_GRACE_PERIOD_IN_SECONDS,
        } as any);
        const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING, {});
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        await login(studentOne);
        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);

        const canvas = page.locator('.apollon-editor');
        await expect(canvas).toBeVisible();
        // The palette is what a student needs to model at all, and it lives inside
        // the canvas — so it is the honest check that the editor is usable.
        await expect(page.locator('[data-testid="apollon-palette"], [data-apollon-control="apollon:palette"]').first()).toBeVisible();

        const host = page.locator('jhi-modeling-submission-exam');
        const [canvasBox, hostBox] = await Promise.all([canvas.boundingBox(), host.boundingBox()]);

        expect(canvasBox!.height).toBeGreaterThan(MIN_USABLE_CANVAS_HEIGHT);
        expect(canvasBox!.height).toBeGreaterThan(hostBox!.height * MIN_SHARE_OF_HOST);
        expect(canvasBox!.y + canvasBox!.height).toBeLessThanOrEqual(hostBox!.y + hostBox!.height + SUB_PIXEL_TOLERANCE);

        // Edge to edge inside "Your Solution": no card padding, no row gutters.
        const cardBody = (await page.locator('.left-body').boundingBox())!;
        const frame = (await page.locator('.modeling-editor__frame').boundingBox())!;
        for (const [edge, delta] of Object.entries({
            left: frame.x - cardBody.x,
            top: frame.y - cardBody.y,
            right: cardBody.x + cardBody.width - (frame.x + frame.width),
            bottom: cardBody.y + cardBody.height - (frame.y + frame.height),
        })) {
            expect(Math.abs(delta), `the editor must sit flush against the card's ${edge} edge`).toBeLessThanOrEqual(1);
        }

        await expectNoScrollPastApollonCanvas(page);
    });
});
