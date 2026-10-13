import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const sourceCourse = { id: SEED_COURSES.exerciseManagement.id } as any;
const targetCourse = { id: SEED_COURSES.import.id } as any;

/**
 * Exercise groups of another exam are imported into an exam that exists already: the instructor picks the source exam, chooses which exercises to
 * take over, and the exam receives copies of the chosen exercises in groups of their own. The groups it had stay, the source exam is not touched, and
 * the exercises that were not chosen are not imported.
 */
test.describe('Exam exercise group import', { tag: '@slow' }, () => {
    let sourceExam: Exam;
    let targetExam: Exam;

    test.afterEach('Delete the exams', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(targetExam);
        await examAPIRequests.deleteExam(sourceExam);
    });

    test('Chosen exercises of another exam are imported as copies into new groups', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        const uid = generateUUID();
        sourceExam = await examAPIRequests.createExam({ course: sourceCourse, title: `Group source ${uid}`, numberOfExercisesInExam: 2, examMaxPoints: 20 });
        const chosenGroup = await examAPIRequests.addExerciseGroupForExam(sourceExam, `Chosen group ${uid}`);
        const chosenExercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup: chosenGroup }, `Chosen exercise ${uid}`);
        const skippedGroup = await examAPIRequests.addExerciseGroupForExam(sourceExam, `Skipped group ${uid}`);
        const skippedExercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup: skippedGroup }, `Skipped exercise ${uid}`);

        targetExam = await examAPIRequests.createExam({ course: targetCourse, title: `Group target ${uid}`, numberOfExercisesInExam: 2, examMaxPoints: 20 });
        const ownGroup = await examAPIRequests.addExerciseGroupForExam(targetExam, `Own group ${uid}`);
        const ownExercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup: ownGroup }, `Own exercise ${uid}`);

        // The instructor finds the source exam in the import dialog and opens its exercise groups.
        await login(instructor, `/course-management/${targetCourse.id}/exams/${targetExam.id}/exercise-groups`);
        await page.locator('#import-group').click();
        const dialog = page.getByRole('dialog');
        await dialog.locator('input[name="searchExcercise"]').fill(sourceExam.title!);
        const row = dialog.locator('tbody tr', { hasText: sourceExam.title! });
        await expect(row).toHaveCount(1);
        await row.getByRole('button', { name: 'Select exercise group' }).click();

        // Both exercises are offered; the instructor drops the second one.
        await expect(dialog.locator(`#exercise-${chosenExercise.id}-title`)).toHaveValue(chosenExercise.title!);
        await expect(dialog.locator(`#exercise-${skippedExercise.id}-title`)).toHaveValue(skippedExercise.title!);
        await dialog.locator(`tr[id="exercise-${skippedExercise.id}"] input[type="checkbox"]`).uncheck();

        const imported = page.waitForResponse((response) => response.url().includes('import-exercise-group') && response.request().method() === 'POST', { timeout: 120_000 });
        await dialog.getByRole('button', { name: 'Import' }).click();
        expect((await imported).status()).toBeLessThan(300);
        await expect(page.getByText(/imported successfully/i).first()).toBeVisible({ timeout: 60_000 });
        await page.locator('[data-testid="exam-import-progress-dismiss"]').click();
        await expect(page.getByRole('dialog'), 'the import dialog and the summary are closed after the summary is dismissed').toHaveCount(0);

        // The exam has its own group and a new one with a copy of the chosen exercise; the other exercise was left out.
        const groups = await examAPIRequests.getExerciseGroups(targetExam);
        expect(groups, 'the own group and the imported one').toHaveLength(2);
        expect(groups.find((group) => group.id === ownGroup.id)!.exercises!.map((exercise) => exercise.id)).toEqual([ownExercise.id]);
        const importedGroup = groups.find((group) => group.id !== ownGroup.id)!;
        expect(importedGroup.title).toBe(chosenGroup.title);
        expect(importedGroup.exercises).toHaveLength(1);
        expect(importedGroup.exercises![0].title).toBe(chosenExercise.title);
        expect(importedGroup.exercises![0].type).toBe(chosenExercise.type);
        expect(importedGroup.exercises![0].maxPoints).toBe(chosenExercise.maxPoints);
        expect(importedGroup.exercises![0].id, 'the exercise is a copy').not.toBe(chosenExercise.id);
        expect(groups.flatMap((group) => group.exercises!.map((exercise) => exercise.title))).not.toContain(skippedExercise.title);
        await expect(page.getByTestId('exercise-group-title')).toHaveText([new RegExp(`^${ownGroup.title}`), new RegExp(`^${chosenGroup.title}`)]);

        // The source exam did not change.
        const sourceGroups = await examAPIRequests.getExerciseGroups(sourceExam);
        expect(sourceGroups.map((group) => group.id)).toEqual([chosenGroup.id, skippedGroup.id]);
        expect(sourceGroups.flatMap((group) => group.exercises!.map((exercise) => exercise.id))).toEqual([chosenExercise.id, skippedExercise.id]);
    });
});
