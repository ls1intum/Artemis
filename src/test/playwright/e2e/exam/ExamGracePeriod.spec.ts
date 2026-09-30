import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { asAdmin, prepareRunningTextExam } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ModalDialogBox } from '../../support/pageobjects/exam/ModalDialogBox';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';

const course = { id: SEED_COURSES.examParticipation.id } as any;
const textFixture = 'loremIpsum-short.txt';

/**
 * The grace period is the time after the regular end of an exam in which a student can still hand in. These tests take a student
 * into a running exam and let an admin end that exam a few seconds later, so the timing is set by the server clock once the student is
 * already working, not guessed at creation time.
 */
test.describe('Exam grace period', { tag: '@slow' }, () => {
    let exam: Exam;

    async function prepareRunningExamWithTextExercise(gracePeriodInSeconds: number, examAPIRequests: ExamAPIRequests, exerciseAPIRequests: ExerciseAPIRequests) {
        const prepared = await prepareRunningTextExam(examAPIRequests, exerciseAPIRequests, { course, examOptions: { gracePeriod: gracePeriodInSeconds } });
        exam = prepared.exam;
        const { exerciseGroup, exercise } = prepared;
        return { exerciseGroup, exercise };
    }

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('A student who hands in during the grace period is accepted', async ({
        browser,
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
        examStartEnd,
    }) => {
        await login(admin);
        const { exerciseGroup, exercise } = await prepareRunningExamWithTextExercise(60, examAPIRequests, exerciseAPIRequests);
        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        await examParticipation.makeTextExerciseSubmission(exercise.id!, textFixture);
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);

        const end = await asAdmin(browser, (adminExamRequests) => adminExamRequests.endExamIn(exam, 8));
        const workingTimeDialog = new ModalDialogBox(page);
        await workingTimeDialog.checkDialogMessage('The working time of the exam has been changed.');
        await workingTimeDialog.closeDialog();
        await examParticipation.checkExamFinishedTitle(exam.title!);

        // The regular end has passed on the server too, so the hand-in below happens inside the grace period and not before it.
        await asAdmin(browser, (adminExamRequests) => adminExamRequests.waitUntilServerClockIsAfter(exam, end));
        const response = await examStartEnd.finishExam();
        expect(response.status()).toBe(200);

        const [studentExam] = await asAdmin(browser, (adminExamRequests) => adminExamRequests.getAllStudentExams(exam));
        expect(studentExam.submitted).toBe(true);
        expect(dayjs(studentExam.submissionDate).isAfter(end), 'the hand-in was after the regular end').toBe(true);
        expect(dayjs(studentExam.submissionDate).isBefore(end.add(60, 'seconds')), 'the hand-in was inside the grace period').toBe(true);

        // What the student typed before the exam ran out is what was handed in.
        await examStartEnd.pressShowSummary();
        await examParticipation.verifyTextExerciseOnFinalPage(exercise.id!, textFixture);
    });

    test('A student who has not handed in when the grace period is over is told so and cannot hand in any more', async ({
        browser,
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
    }) => {
        await login(admin);
        const { exerciseGroup, exercise } = await prepareRunningExamWithTextExercise(4, examAPIRequests, exerciseAPIRequests);
        const [studentExam] = await examAPIRequests.getAllStudentExams(exam);
        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        await examParticipation.makeTextExerciseSubmission(exercise.id!, textFixture);
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);

        await asAdmin(browser, (adminExamRequests) => adminExamRequests.endExamIn(exam, 6));
        await new ModalDialogBox(page).closeDialog();
        await asAdmin(browser, (adminExamRequests) => adminExamRequests.waitUntilExamIsOver(exam));

        // The student is told that the exam was not handed in on time, and there is nothing left to hand in with.
        await expect(page.getByText('You have not submitted your exam on time. It will not be graded!')).toBeVisible();
        await expect(page.getByTestId('end-exam')).toHaveCount(0);
        await expect(page.getByTestId('hand-in-early')).toHaveCount(0);

        // The server agrees: it refuses a late hand-in and the student exam stays unsubmitted.
        const lateHandIn = await page.request.post(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/submit`, { data: { id: studentExam.id, exercises: [] } });
        expect(lateHandIn.status()).toBe(403);
        const [afterwards] = await asAdmin(browser, (adminExamRequests) => adminExamRequests.getAllStudentExams(exam));
        expect(afterwards.submitted).toBe(false);
    });
});
