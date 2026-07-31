import { test } from '../../support/fixtures';
import { ExerciseType } from '../../support/constants';
import { admin, studentOne } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../support/seedData';
import dayjs from 'dayjs';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/**
 * Regression test for the exam save-state indicators, which tell a student whether their answer has
 * reached the server. They are driven by `Submission.isSynced`, which every exam editor mutates **in
 * place** on a plain object. Under zoneless change detection that mutation schedules no re-render on its
 * own, so each producer calls `ExamParticipationService.notifySubmissionSyncStateChanged()` and each
 * consumer reads `submissionSyncVersion()`. When a consumer forgets to read it, the indicator silently
 * freezes: the student edits an answer and the UI keeps claiming it is saved until some unrelated click
 * happens to trigger change detection. During an exam that hides the unsaved-changes warning, which is
 * why this is worth an end-to-end test.
 *
 * Unit tests cannot catch it. They drive change detection explicitly, so a binding that never declares a
 * dependency on the version signal still appears to update. Only a real browser under real zoneless
 * change detection distinguishes the two, so this test asserts purely by polling the DOM and never
 * performs an unrelated interaction that could mask a missing re-render.
 *
 * Covers both consumers of the signal in the conduction view: the navigation sidebar's per-exercise
 * status icon (`getExerciseButtonStatus`, rendered as the `synced` / `synced saved` / `notSynced` class)
 * and the save button (`ExerciseSaveButtonComponent`, whose `disabled` state mirrors `isSynced`).
 */
test.describe('Exam unsaved changes indicator', { tag: '@slow' }, () => {
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

    test('shows unsaved changes as soon as the student edits, and clears it on save', async ({ page, examParticipation, examNavigation }) => {
        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);

        const sidebar = page.locator('jhi-exam-navigation-sidebar');
        const unsavedStatus = sidebar.locator('span.notSynced');
        const savedStatus = sidebar.locator('span.saved');
        const saveButton = page.locator('#save-exam').first();
        const editor = page.locator('#text-editor').first();
        await editor.waitFor({ state: 'visible', timeout: 30000 });

        // Typing mutates isSynced in place. Nothing else here can trigger change detection, so both
        // indicators must react on their own. Each assertion resolves on the first poll that satisfies it,
        // well before the exam's 30s autosave could flip the flag back and hide a missing re-render.
        await editor.click();
        await editor.pressSequentially('Answer to the first exam question', { delay: 20 });
        await expect(unsavedStatus, 'the sidebar must warn about unsaved changes right after the student types').toHaveCount(1, { timeout: 20000 });
        await expect(saveButton, 'the save button must become clickable once there are unsaved changes').toBeEnabled({ timeout: 20000 });

        // Saving flips isSynced and submitted back to true, again by in-place mutation.
        await saveButton.click();
        await expect(savedStatus, 'the sidebar must show the saved state after a successful save').toHaveCount(1, { timeout: 20000 });
        await expect(unsavedStatus, 'the unsaved warning must disappear after a successful save').toHaveCount(0);
        await expect(saveButton, 'the save button must disable itself again once the submission is synced').toBeDisabled({ timeout: 20000 });

        // The regression proper: editing an already-saved submission must bring the warning back without
        // any unrelated interaction.
        await editor.click();
        await editor.pressSequentially(' and a correction', { delay: 20 });
        await expect(unsavedStatus, 'editing a saved submission must warn about unsaved changes again').toHaveCount(1, { timeout: 20000 });
        await expect(saveButton, 'the save button must re-enable after the saved submission is edited').toBeEnabled({ timeout: 20000 });
    });
});
