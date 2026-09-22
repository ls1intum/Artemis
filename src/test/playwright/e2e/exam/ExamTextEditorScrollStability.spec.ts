import { expect } from '@playwright/test';
import dayjs from 'dayjs';

import { test } from '../../support/fixtures';
import { ExerciseType } from '../../support/constants';
import { SEED_COURSES } from '../../support/seedData';
import { admin, studentOne } from '../../support/users';
import { generateUUID } from '../../support/utils';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/**
 * Regression test for the exam conduction view's exercise column (#13916).
 *
 * The column used to take its layout classes from a `scrollHeight > clientHeight` measurement of the very
 * container those classes size, so each state produced the other: stretched to `h-100` the column fit, so
 * the classes came back, so it stretched again. Because the class was recomputed on every change-detection
 * pass and read the previous frame's layout, the scroll bar appeared and disappeared while nothing on the
 * page changed, and whether it settled or oscillated depended on the exact content height. It is sized by
 * CSS alone now (`min-h-100`).
 *
 * A component test cannot catch this. jsdom has no layout engine, so `scrollHeight` and `clientHeight` are
 * both 0 there and every arrangement looks identical; the unit test can only assert which classes are
 * bound. Only a real browser reports the overflow that drove the loop, which is what this test samples.
 */
test.describe('Exam text editor scroll stability', { tag: '@slow' }, () => {
    let exam: any;
    let textExercise: any;

    test.beforeEach('Create an exam with a text exercise', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({
            course,
            title: 'exam' + generateUUID(),
            visibleDate: dayjs().subtract(3, 'minutes'),
            startDate: dayjs().subtract(2, 'minutes'),
            endDate: dayjs().add(1, 'hour'),
            examMaxPoints: 10,
            numberOfExercisesInExam: 1,
        });
        textExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture: 'loremIpsum.txt' });
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
    });

    test('keeps the scroll bar steady and only shows it when the exercise really is taller', async ({ page, examParticipation, examNavigation }) => {
        await page.setViewportSize({ width: 1400, height: 1000 });
        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
        await expect(page.locator('#text-editor')).toBeVisible({ timeout: 30000 });

        const scrollContainer = page.locator('.content-exam-height');

        /** How far the exercise column overflows its scroll container, sampled repeatedly over ~2s. */
        const sampleOverflow = () =>
            scrollContainer.evaluate(async (container) => {
                const samples: number[] = [];
                for (let i = 0; i < 20; i++) {
                    samples.push(container.scrollHeight - container.clientHeight);
                    await new Promise((resolve) => requestAnimationFrame(() => setTimeout(resolve, 100)));
                }
                return samples;
            });

        // The regression proper, in both of its shapes. The feedback loop alternated between "fits" and
        // "overflows" on successive change-detection passes, which the exam timer alone triggers once a
        // second, so a stable layout reports one value here and the old one reported several. Where the old
        // layout did settle it often settled on the wrong one: `h-100` left a permanent ~10px overflow, a
        // scroll bar with nothing to scroll, so the stable value has to be zero rather than merely stable.
        // This viewport is not a marginal case: the column first overflows below roughly 850px of height.
        const tallViewportSamples = await sampleOverflow();
        expect(new Set(tallViewportSamples).size, `the scroll bar flickered: overflow went through ${JSON.stringify([...new Set(tallViewportSamples)])}`).toBe(1);
        expect(tallViewportSamples[0], 'an exercise that fits its column must not leave a scroll bar behind').toBe(0);

        // `min-h-100` makes the column fill its scroll container's content box, which is what keeps the
        // connection-status footer at the bottom while an exercise is short. The measurement subtracts the
        // container's padding, because that is what a percentage height resolves against; the old code
        // dropped the class whenever it measured overflow, leaving the column sized by its content instead.
        const [columnHeight, contentHeight] = await scrollContainer.evaluate((container) => {
            const styles = getComputedStyle(container);
            const padding = parseFloat(styles.paddingTop) + parseFloat(styles.paddingBottom);
            return [(container.firstElementChild as HTMLElement).getBoundingClientRect().height, container.clientHeight - padding];
        });
        expect(columnHeight).toBeGreaterThanOrEqual(contentHeight - 1);

        // And the other half: a viewport too short for the editor must still scroll, so the fix cannot have
        // simply suppressed the scroll bar. Deliberately far below the flicker threshold, so the assertion
        // does not depend on where exactly that threshold falls on a given machine.
        await page.setViewportSize({ width: 1400, height: 600 });
        const shortViewportSamples = await sampleOverflow();
        expect(new Set(shortViewportSamples).size, `the scroll bar flickered: overflow went through ${JSON.stringify([...new Set(shortViewportSamples)])}`).toBe(1);
        expect(shortViewportSamples[0]).toBeGreaterThan(0);
    });
});
