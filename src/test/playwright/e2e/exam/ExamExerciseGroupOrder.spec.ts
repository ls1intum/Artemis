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
    test('An instructor drags an exercise into another group and the move is saved', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course, numberOfExercisesInExam: 2, examMaxPoints: 20 });
        const uid = generateUUID();
        const groupA = await examAPIRequests.addExerciseGroupForExam(exam, 'Group A ' + uid);
        const groupB = await examAPIRequests.addExerciseGroupForExam(exam, 'Group B ' + uid);
        const movedExercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup: groupA }, 'Moved ' + uid);
        const stayingExercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup: groupB }, 'Staying ' + uid);

        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/exercise-groups`);
        const groupOf = (title: string) => page.locator('[id^="group-"]', { hasText: title });
        const movedRow = page.locator(`#exercise-${movedExercise.id}`);
        await expect(groupOf(groupA.title!).locator(`#exercise-${movedExercise.id}`)).toBeVisible();

        // The exercise is picked up at its handle and dropped onto a row of the other group.
        const handle = (await movedRow.getByTestId('exercise-drag-handle').boundingBox())!;
        const target = (await page.locator(`#exercise-${stayingExercise.id}`).boundingBox())!;
        const moved = page.waitForResponse((response) => response.url().includes(`/exercises/${movedExercise.id}/exercise-group`), { timeout: 30_000 });
        await page.mouse.move(handle.x + handle.width / 2, handle.y + handle.height / 2);
        await page.mouse.down();
        await page.mouse.move(handle.x + 30, handle.y + 20, { steps: 5 });
        await page.mouse.move(target.x + 200, target.y + target.height / 2, { steps: 20 });
        await page.mouse.up();
        expect((await moved).status()).toBe(200);

        // The exercise is listed in the second group now, the first group has none left, and the move survives a reload.
        await expect(groupOf(groupB.title!).locator(`#exercise-${movedExercise.id}`)).toBeVisible();
        await expect(groupOf(groupA.title!).locator(`#exercise-${movedExercise.id}`)).toHaveCount(0);
        await page.reload();
        await expect(groupOf(groupB.title!).locator(`#exercise-${movedExercise.id}`)).toBeVisible();
        const groups = await examAPIRequests.getExerciseGroups(exam);
        const titlesIn = (title: string) => (groups.find((group) => group.title === title)!.exercises ?? []).map((exercise) => exercise.title);
        expect(titlesIn(groupA.title!)).toEqual([]);
        expect(titlesIn(groupB.title!).sort()).toEqual([movedExercise.title, stayingExercise.title].sort());
    });
});
