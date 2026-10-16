import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { ExerciseType } from '../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

const sourceCourse = { id: SEED_COURSES.exerciseManagement.id } as any;
const targetCourse = { id: SEED_COURSES.import.id } as any;

/**
 * Importing an exam from another course copies its configuration, its exercise groups and its exercises into the target course. The copy is
 * an exam of its own: it has its own exercises, and the source exam stays as it was.
 */
test.describe('Exam import', { tag: '@slow' }, () => {
    let sourceExam: Exam;
    let importedExam: Exam | undefined;

    test.afterEach('Delete the exams', async ({ login, examAPIRequests }) => {
        await login(admin);
        if (importedExam) {
            await examAPIRequests.deleteExam(importedExam);
            importedExam = undefined;
        }
        await examAPIRequests.deleteExam(sourceExam);
    });

    test('An exam is imported from another course with its groups and exercises, and stays independent of its source', async ({
        page,
        login,
        examAPIRequests,
        examExerciseGroupCreation,
        examCreation,
    }) => {
        // The source exam: two exercise groups with one exercise each, worth twenty points.
        await login(admin);
        const uid = generateUUID();
        sourceExam = await examAPIRequests.createExam({
            course: sourceCourse,
            title: 'Import Source ' + uid,
            numberOfExercisesInExam: 2,
            examMaxPoints: 20,
        });
        await examExerciseGroupCreation.addGroupWithExercise(sourceExam, ExerciseType.TEXT, { textFixture: 'loremIpsum-short.txt' });
        await examExerciseGroupCreation.addGroupWithExercise(sourceExam, ExerciseType.QUIZ, { quizExerciseID: 0 });
        const sourceGroups = await examAPIRequests.getExerciseGroups(sourceExam);
        expect(sourceGroups).toHaveLength(2);

        // The instructor of the target course finds the exam in the import dialog and starts the import.
        await login(instructor, `/course-management/${targetCourse.id}/exams`);
        await page.getByTestId('import-exam-button').click();
        const dialog = page.getByRole('dialog');
        await dialog.locator('input[name="searchExcercise"]').fill(sourceExam.title!);
        const row = dialog.locator('tbody tr', { hasText: sourceExam.title! });
        await expect(row).toHaveCount(1);
        await expect(row).toContainText(SEED_COURSES.exerciseManagement.title ?? '');
        await row.getByRole('button', { name: 'Import' }).click();
        await page.waitForURL(new RegExp(`/course-management/${targetCourse.id}/exams/import/${sourceExam.id}$`));
        await expect(dialog, 'the selection dialog closes once the exam is chosen').toBeHidden();

        // The import page offers every group and exercise of the source with its title.
        for (const [index, group] of sourceGroups.entries()) {
            await expect(page.locator(`#exerciseGroup-${index}-title`)).toHaveValue(group.title!);
            const exercise = group.exercises![0];
            await expect(page.locator(`#exercise-${exercise.id}-title`)).toHaveValue(exercise.title!);
        }

        // The conduction dates are stripped on import and have to be given again; the title is changed so that the copy can be told apart.
        const importedTitle = 'Imported ' + uid;
        await examCreation.setTitle(importedTitle);
        await examCreation.setVisibleDate(dayjs());
        await examCreation.setStartDate(dayjs().add(1, 'hour'));
        await examCreation.setEndDate(dayjs().add(2, 'hours'));
        await expect(page.locator('#save-exam')).toBeEnabled();
        const imported = page.waitForResponse((response) => response.url().includes('/exam-import') && response.request().method() === 'POST', { timeout: 120_000 });
        await page.locator('#save-exam').click();
        expect((await imported).status()).toBe(201);
        await expect(page.getByText(/imported successfully/i)).toBeVisible({ timeout: 60_000 });
        await page.locator('[data-testid="exam-import-progress-dismiss"]').click();
        await page.waitForURL(new RegExp(`/course-management/${targetCourse.id}/exams/\\d+$`));
        importedExam = { id: Number(page.url().split('/').pop()), course: targetCourse } as Exam;
        expect(importedExam.id).not.toBe(sourceExam.id);

        // The copy holds the configuration of the source and the dates that were entered ...
        await login(admin);
        const storedCopy = await examAPIRequests.getExam(importedExam);
        expect(storedCopy.title).toBe(importedTitle);
        expect(storedCopy.numberOfExercisesInExam).toBe(2);
        expect(storedCopy.examMaxPoints).toBe(20);
        const storedSource = await examAPIRequests.getExam(sourceExam);
        expect(storedCopy.startText, 'the start text of the source is copied').toBe(storedSource.startText);
        expect(storedCopy.endText).toBe(storedSource.endText);
        expect(storedCopy.confirmationStartText).toBe(storedSource.confirmationStartText);
        expect(storedCopy.confirmationEndText).toBe(storedSource.confirmationEndText);
        expect(storedCopy.testExam).toBe(false);
        expect(dayjs(storedCopy.endDate as any).diff(dayjs(storedCopy.startDate as any), 'minutes')).toBe(60);

        // ... and its own copies of every group and exercise, with the same titles, types and points.
        const copiedGroups = await examAPIRequests.getExerciseGroups(importedExam);
        expect(copiedGroups.map((group: ExerciseGroup) => group.title)).toEqual(sourceGroups.map((group: ExerciseGroup) => group.title));
        for (const [index, group] of copiedGroups.entries()) {
            const original = sourceGroups[index];
            expect(group.id, 'the group is a copy').not.toBe(original.id);
            expect(group.isMandatory).toBe(original.isMandatory);
            expect(group.exercises).toHaveLength(1);
            expect(group.exercises![0].id, 'the exercise is a copy').not.toBe(original.exercises![0].id);
            expect(group.exercises![0].title).toBe(original.exercises![0].title);
            expect(group.exercises![0].type).toBe(original.exercises![0].type);
            expect(group.exercises![0].maxPoints).toBe(original.exercises![0].maxPoints);
        }

        // The source exam did not change.
        const sourceAfter = await examAPIRequests.getExerciseGroups(sourceExam);
        expect(sourceAfter.map((group: ExerciseGroup) => group.id)).toEqual(sourceGroups.map((group: ExerciseGroup) => group.id));
        expect(sourceAfter.map((group: ExerciseGroup) => group.exercises![0].id)).toEqual(sourceGroups.map((group: ExerciseGroup) => group.exercises![0].id));
        expect((await examAPIRequests.getExam(sourceExam)).title).toBe(storedSource.title);
    });
});
