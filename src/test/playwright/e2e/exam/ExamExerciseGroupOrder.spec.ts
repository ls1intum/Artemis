import { expect, Page } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;

/**
 * The order of the exercise groups is the order in which students find the exercises in their exam. An instructor changes it with the
 * arrows of the exercise groups page, the change is saved right away, and a student exam generated afterwards follows it.
 */
test.describe('Exam exercise group order', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    /** The titles of the groups in the order in which the page lists them. */
    async function listedGroupTitles(page: Page): Promise<string[]> {
        return (await page.getByTestId('exercise-group-title').allInnerTexts()).map((text) => text.replace(/\s*\(\d+\)$/, ''));
    }

    test('An instructor reorders the exercise groups and students find their exercises in that order', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
    }) => {
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course, numberOfExercisesInExam: 3, examMaxPoints: 30 });
        const uid = generateUUID();
        const titles = ['Group A ' + uid, 'Group B ' + uid, 'Group C ' + uid];
        for (const title of titles) {
            const group = await examAPIRequests.addExerciseGroupForExam(exam, title);
            await exerciseAPIRequests.createTextExercise({ exerciseGroup: group });
        }

        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/exercise-groups`);
        await expect.poll(() => listedGroupTitles(page)).toEqual(titles);

        // The first group can not move up and the last one can not move down.
        const moveUp = (title: string) => page.locator('[id^="group-"]', { hasText: title }).getByRole('button', { name: 'Move exercise group up' });
        const moveDown = (title: string) => page.locator('[id^="group-"]', { hasText: title }).getByRole('button', { name: 'Move exercise group down' });
        await expect(moveUp(titles[0])).toBeDisabled();
        await expect(moveDown(titles[2])).toBeDisabled();

        // Moving B up puts it first; the order is saved right away and survives a reload.
        const saved = page.waitForResponse((response) => response.url().endsWith(`/exams/${exam.id}/exercise-groups-order`) && response.request().method() === 'PUT');
        await moveUp(titles[1]).click();
        expect((await saved).status()).toBe(200);
        await expect.poll(() => listedGroupTitles(page)).toEqual([titles[1], titles[0], titles[2]]);
        await page.reload();
        await expect.poll(() => listedGroupTitles(page)).toEqual([titles[1], titles[0], titles[2]]);
        expect((await examAPIRequests.getExerciseGroups(exam)).map((group) => group.title)).toEqual([titles[1], titles[0], titles[2]]);

        // Moving C up twice puts it first as well.
        for (let step = 0; step < 2; step++) {
            const stepSaved = page.waitForResponse((response) => response.url().endsWith('/exercise-groups-order') && response.request().method() === 'PUT');
            await moveUp(titles[2]).click();
            expect((await stepSaved).status()).toBe(200);
        }
        const finalOrder = [titles[2], titles[1], titles[0]];
        await expect.poll(() => listedGroupTitles(page)).toEqual(finalOrder);

        // A student exam generated now lists the exercises in that order.
        await login(admin);
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
        await examParticipation.startParticipation(studentOne, course, exam);
        await expect(page.getByTestId('sidebar-card-title').filter({ hasText: uid })).toHaveText(finalOrder);
    });
});
