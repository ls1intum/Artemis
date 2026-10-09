import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { getExercise } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { ProgrammingLanguage } from '../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/**
 * How a student may work on a programming exercise of an exam is configured per exercise: in the online editor only, in the online editor
 * and in their own IDE after cloning, or in their own IDE only. The exam shows the student exactly what the exercise allows.
 */
test.describe('Exam programming exercise modes', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('The exam offers the online editor and the repository to clone as the exercise allows', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
    }) => {
        test.slow();
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course, numberOfExercisesInExam: 3, examMaxPoints: 30 });
        const modes = [
            { name: 'online editor only', allowOnlineEditor: true, allowOfflineIde: false },
            { name: 'online editor and own IDE', allowOnlineEditor: true, allowOfflineIde: true },
            { name: 'own IDE only', allowOnlineEditor: false, allowOfflineIde: true },
        ];
        const exercises: { mode: (typeof modes)[number]; exercise: ProgrammingExercise; groupTitle: string }[] = [];
        for (const mode of modes) {
            const group = await examAPIRequests.addExerciseGroupForExam(exam, `Group ${mode.name}`);
            const exercise = await exerciseAPIRequests.createProgrammingExercise({
                exerciseGroup: group,
                programmingLanguage: ProgrammingLanguage.C,
                allowOnlineEditor: mode.allowOnlineEditor,
                allowOfflineIde: mode.allowOfflineIde,
            });
            expect(exercise.allowOnlineEditor, `${mode.name}: the server stored the online editor flag`).toBe(mode.allowOnlineEditor);
            expect(exercise.allowOfflineIde, `${mode.name}: the server stored the offline IDE flag`).toBe(mode.allowOfflineIde);
            exercises.push({ mode, exercise, groupTitle: group.title! });
        }
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        await examParticipation.startParticipation(studentOne, course, exam);
        for (const { mode, exercise, groupTitle } of exercises) {
            await examNavigation.openOrSaveExerciseByTitle(groupTitle);
            const section = getExercise(page, exercise.id!);
            await expect(section.locator('jhi-programming-exercise-instructions'), `${mode.name}: the problem statement is there`).toBeVisible();
            await expect(section.locator('jhi-code-editor-container'), `${mode.name}: online editor`).toHaveCount(mode.allowOnlineEditor ? 1 : 0);
            await expect(section.locator('jhi-exercise-details-student-actions'), `${mode.name}: repository to clone`).toHaveCount(mode.allowOfflineIde ? 1 : 0);
        }
    });
});
