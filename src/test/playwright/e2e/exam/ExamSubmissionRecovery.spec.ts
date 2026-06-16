import { test } from '../../support/fixtures';
import { expect } from '@playwright/test';
import { admin, studentTwo } from '../../support/users';
import { generateUUID, getExercise } from '../../support/utils';
import dayjs from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Exercise, ExerciseType } from '../../support/constants';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { SEED_COURSES } from '../../support/seedData';

/**
 * Regression test for silent exam answer loss after a failed save.
 *
 * When a submission save fails (e.g. during a network/power outage) the answer stays unsynced and is written to local
 * storage. On reload the client must RESTORE the answer from local storage AND re-send it to the server, instead of
 * marking it as already synced and silently dropping it.
 *
 * This test ticks a multiple-choice answer, forces the save to fail, reloads the page, and verifies that the answer is
 * both restored in the UI and successfully re-sent to the server.
 */
const course = { id: SEED_COURSES.examParticipation.id } as any;
const quizSaveUrl = '**/api/quiz/exercises/*/submissions/exam';

test.describe('Exam submission recovery after a failed save', { tag: '@slow' }, () => {
    let exam: Exam;
    let quizExercise: Exercise;

    test.beforeEach('Create exam with a multiple-choice quiz', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
        await login(admin);
        exam = await createExam(course, examAPIRequests, { title: 'exam' + generateUUID() });
        quizExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 });
        await examAPIRequests.registerStudentForExam(exam, studentTwo);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
    });

    test('restores and re-sends a not-yet-saved quiz answer after a failed save and reload', async ({ page, examParticipation, examNavigation, quizExerciseMultipleChoice }) => {
        await examParticipation.startParticipation(studentTwo, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(quizExercise.exerciseGroup!.title!);

        // Tick an answer option; the exercise becomes unsynced.
        await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 0);
        await expect(getExercise(page, quizExercise.id!).locator('#answer-option-0')).toHaveClass(/selected/);

        // Simulate a failed save (as during an outage): make the quiz exam save endpoint fail.
        await page.route(quizSaveUrl, (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{}' }));
        // Force a save attempt. The answer is written to local storage but the server submission stays empty.
        await getExercise(page, quizExercise.id!).locator('#save-exam').click();
        await page.waitForTimeout(1000);
        // Stop failing saves so the post-reload re-send can succeed.
        await page.unroute(quizSaveUrl);

        // On reload the restored answer must be re-sent to the server (successful PUT).
        const reSavePromise = page.waitForResponse(
            (response) => response.url().includes('/submissions/exam') && response.request().method() === 'PUT' && response.status() === 200,
            { timeout: 30000 },
        );
        await page.reload();
        await reSavePromise;

        // The restored answer is still selected in the UI.
        await examNavigation.openOrSaveExerciseByTitle(quizExercise.exerciseGroup!.title!);
        await expect(getExercise(page, quizExercise.id!).locator('#answer-option-0')).toHaveClass(/selected/, { timeout: 15000 });
    });

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });
});

async function createExam(course: any, examAPIRequests: ExamAPIRequests, customExamConfig?: any) {
    const defaultExamConfig = {
        course,
        title: 'exam' + generateUUID(),
        visibleDate: dayjs().subtract(3, 'minutes'),
        startDate: dayjs().subtract(2, 'minutes'),
        endDate: dayjs().add(1, 'hour'),
        examMaxPoints: 10,
        numberOfExercisesInExam: 1,
    };
    return await examAPIRequests.createExam({ ...defaultExamConfig, ...customExamConfig });
}
