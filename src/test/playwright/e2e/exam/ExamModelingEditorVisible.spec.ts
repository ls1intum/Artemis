import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { admin, studentOne } from '../../support/users';
import { test } from '../../support/fixtures';
import { SEED_COURSES } from '../../support/seedData';
import { EXAM_GRACE_PERIOD_IN_SECONDS, ExerciseType } from '../../support/constants';
import { expectNoScrollPastApollonCanvas } from '../../support/utils';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

/**
 * The exam modeling canvas once rendered as a blank panel: the exercise wrapper
 * was content-sized, so a canvas that cannot derive a height from its own content
 * collapsed and painted outside its clipped box. Nothing caught it, because the
 * existing exam coverage drives the editor through the API-shaped page object
 * rather than asserting that the canvas is on screen and usable.
 *
 * This asserts the canvas is actually there, actually fills the space it is given,
 * and — the invariant every Apollon surface shares — cannot be scrolled past.
 *
 * Deliberately does NOT use `prepareExam`: that helper hands in early, after which
 * the student can no longer enter the exam.
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

        // The canvas must fill the page, not sit in a collapsed box: a real height,
        // and one that is most of what the exercise page offers it.
        expect(canvasBox!.height).toBeGreaterThan(400);
        expect(canvasBox!.height).toBeGreaterThan(hostBox!.height * 0.6);
        // And it must be inside its host, which is what failed when the host collapsed.
        expect(canvasBox!.y + canvasBox!.height).toBeLessThanOrEqual(hostBox!.y + hostBox!.height + 1);

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
