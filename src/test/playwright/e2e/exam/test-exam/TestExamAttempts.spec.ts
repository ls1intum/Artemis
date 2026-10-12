import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../../support/fixtures';
import { admin, studentOne } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';
import { ExerciseType } from '../../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { expectStoredAnswers } from '../../../support/examAnswerAssertions';
import { asAdmin } from '../../../support/utils';

const course = { id: SEED_COURSES.testExam.id } as any;

/**
 * A test exam can be taken as often as a student wants while it is open: every attempt is a student exam of its own, a student registers
 * themselves by starting the first one, and an exam that is over can not be started.
 */
test.describe('Test exam attempts', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('A student can take several attempts, each with an answer of its own', async ({
        page,
        login,
        examAPIRequests,
        examExerciseGroupCreation,
        examParticipation,
        examNavigation,
        examStartEnd,
    }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({
            course,
            testExam: true,
            startDate: dayjs().subtract(1, 'day'),
            visibleDate: dayjs().subtract(2, 'days'),
            examMaxPoints: 10,
            numberOfExercisesInExam: 1,
            workingTime: 3600,
        });
        const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture: 'loremIpsum-short.txt' });
        const answers = ['loremIpsum-short.txt', 'loremIpsum.txt'];

        const attemptIds: number[] = [];
        for (const [index, textFixture] of answers.entries()) {
            // The student registers themselves by starting the first attempt, and can start the next one as soon as the last is handed in.
            await examParticipation.startParticipation(studentOne, course, exam);
            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            await examParticipation.makeTextExerciseSubmission(exercise.id!, textFixture);
            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();
            await examParticipation.verifyTextExerciseOnFinalPage(exercise.id!, textFixture);

            const attempts = await examAPIRequests.getOwnTestExamAttempts(exam);
            expect(attempts.filter((attempt) => attempt.submitted)).toHaveLength(index + 1);
            attemptIds.push(Math.max(...attempts.map((attempt) => attempt.id!)));
        }

        // The attempts are distinct student exams, and each holds only the answer that was given in it.
        expect(new Set(attemptIds).size).toBe(2);
        for (const [index, attemptId] of attemptIds.entries()) {
            const summary = await examAPIRequests.getStudentExamSummary(exam, attemptId);
            expect(summary.submitted).toBe(true);
            await expectStoredAnswers(summary, [{ ...exercise, additionalData: { textFixture: answers[index] } }], { [ExerciseType.TEXT]: true });
        }

        // The course sidebar includes attempts from other exams running in parallel. Check this exam's two links, not its global count.
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        const attemptsHeader = page.locator('#test-accordion-item-header-attempt');
        await expect(attemptsHeader).toContainText('Test Exam Attempts');
        if ((await attemptsHeader.getAttribute('aria-expanded')) !== 'true') {
            await attemptsHeader.click();
        }
        for (const [index, attemptId] of attemptIds.entries()) {
            const attemptLink = page.locator(`a[href$="/exams/${exam.id}/test-exam/${attemptId}"]`);
            await expect(attemptLink.getByTestId('sidebar-card-title')).toHaveText(exam.title!);
            await expect(attemptLink.getByText(`Attempt ${index + 1}`, { exact: true })).toBeVisible();
        }
    });

    test('A student registers themselves by starting a test exam, and an attempt lasts the working time', async ({
        page,
        browser,
        login,
        examAPIRequests,
        examExerciseGroupCreation,
        examParticipation,
    }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({
            course,
            testExam: true,
            startDate: dayjs().subtract(1, 'day'),
            visibleDate: dayjs().subtract(2, 'days'),
            examMaxPoints: 10,
            numberOfExercisesInExam: 1,
            workingTime: 3600,
        });
        await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture: 'loremIpsum-short.txt' });
        const deletionSummary = async () => await asAdmin(browser, async (adminExamRequests) => await adminExamRequests.getDeletionSummary(exam));
        expect(await deletionSummary(), 'nobody is registered before the first attempt').toMatchObject({ numberRegisteredStudents: 0, numberStartedExams: 0 });

        // Starting the first attempt registers the student: nobody had to do it for them.
        await examParticipation.startParticipation(studentOne, course, exam);
        expect(await deletionSummary()).toMatchObject({ numberRegisteredStudents: 1, numberStartedExams: 1, numberSubmittedExams: 0 });

        // The attempt runs for the working time of the exam from the moment it was started, not until the end of the exam a day later.
        const [attempt] = await examAPIRequests.getOwnTestExamAttempts(exam);
        expect(attempt.workingTime).toBe(3600);
        expect(attempt.started).toBe(true);
        expect(attempt.startedDate).toBeTruthy();
        await expect(page.getByTestId('displayTime')).toContainText('59min');
    });

    test('A test exam that is over can not be started', async ({ page, login, examAPIRequests, examExerciseGroupCreation }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({
            course,
            testExam: true,
            startDate: dayjs().subtract(2, 'days'),
            visibleDate: dayjs().subtract(3, 'days'),
            endDate: dayjs().subtract(1, 'hour'),
            examMaxPoints: 10,
            numberOfExercisesInExam: 1,
            workingTime: 3600,
        });
        await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture: 'loremIpsum-short.txt' });

        await login(studentOne);
        const attempt = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/own-student-exam`);
        expect(attempt.status()).toBe(400);
        expect((await attempt.json()).errorKey).toBe('examHasAlreadyEnded');
        expect(await examAPIRequests.getOwnTestExamAttempts(exam), 'no attempt was created').toHaveLength(0);

        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        // The page has rendered the exam, and offers no way to start it.
        await expect(page.getByText(exam.title!).first()).toBeVisible();
        await expect(page.getByTestId('start-exam')).toHaveCount(0);
    });
});
