import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';
import { expectStoredAnswers } from '../../support/examAnswerAssertions';
import { ExerciseType } from '../../support/constants';

const course = { id: SEED_COURSES.examParticipation.id } as any;
const textFixture = 'loremIpsum-short.txt';

/**
 * Who may see and take an exam: the exam is only for the students registered for it, and a student who is registered late for an exam that
 * has already started gets a student exam right away.
 */
test.describe('Exam access', { tag: '@slow' }, () => {
    let exams: Exam[];

    test.beforeEach(() => {
        exams = [];
    });

    test.afterEach('Delete exams', async ({ login, examAPIRequests }) => {
        await login(admin);
        for (const exam of exams) {
            await examAPIRequests.deleteExam(exam);
        }
    });

    /** Creates a running exam with one text exercise. */
    async function createRunningExamWithTextExercise(examAPIRequests: ExamAPIRequests, exerciseAPIRequests: ExerciseAPIRequests) {
        const exam = await examAPIRequests.createRunningExam({ course });
        exams.push(exam);
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        const exercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        return { exam, exerciseGroup, exercise };
    }

    test('A student who is not registered for an exam neither sees nor gets it', async ({ page, login, examAPIRequests, exerciseAPIRequests, courseOverview }) => {
        await login(admin);
        const registered = await createRunningExamWithTextExercise(examAPIRequests, exerciseAPIRequests);
        const notRegistered = await createRunningExamWithTextExercise(examAPIRequests, exerciseAPIRequests);
        await examAPIRequests.registerStudentForExam(registered.exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(registered.exam);
        await examAPIRequests.prepareExerciseStartForExam(registered.exam);

        await login(studentOne);
        await page.goto(`/courses/${course.id}`);
        await courseOverview.openExamsTab();
        // The exam the student is registered for is the control: the list is there, and it holds exactly that exam of the two.
        await expect(page.getByText(registered.exam.title!).first()).toBeVisible();
        await expect(page.getByText(notRegistered.exam.title!)).toHaveCount(0);

        // The server enforces the same, whichever way the student asks for the exam.
        const overview = await page.request.get(`api/exam/courses/${course.id}/exams-for-overview`);
        const overviewIds = ((await overview.json()) as { id: number }[]).map((exam) => exam.id);
        expect(overviewIds).toContain(registered.exam.id);
        expect(overviewIds).not.toContain(notRegistered.exam.id);
        expect((await page.request.get(`api/exam/courses/${course.id}/exams/${notRegistered.exam.id}/own-student-exam`)).status()).toBe(403);
        expect((await page.request.get(`api/exam/courses/${course.id}/exams/${registered.exam.id}/own-student-exam`)).status()).toBe(200);

        // Opening the exam by its address does not get the student into it either.
        await page.goto(`/courses/${course.id}/exams/${notRegistered.exam.id}`);
        await expect(page.getByText('You are not authorized to access this page.')).toBeVisible();
        await expect(page.getByTestId('start-exam')).toHaveCount(0);
        await expect(page.getByTestId('hand-in-early')).toHaveCount(0);
    });

    test('An instructor can not take the exam of their own course', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        const { exam } = await createRunningExamWithTextExercise(examAPIRequests, exerciseAPIRequests);
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);

        await login(instructor);
        expect((await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/own-student-exam`)).status()).toBe(403);
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        await expect(page.getByText('Since you are not a student, you cannot participate in this exam')).toBeVisible();
        await expect(page.getByTestId('start-exam')).toHaveCount(0);
        await expect(page.getByTestId('hand-in-early')).toHaveCount(0);
    });

    test('A student who is registered after the exam has started gets a student exam right away and can take the exam', async ({
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
        examStartEnd,
    }) => {
        await login(admin);
        const { exam, exerciseGroup, exercise } = await createRunningExamWithTextExercise(examAPIRequests, exerciseAPIRequests);
        expect(await examAPIRequests.getAllStudentExams(exam), 'nobody is registered yet, so there are no student exams').toHaveLength(0);

        // No instructor generates anything: registering for a running exam is enough.
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await expect.poll(async () => (await examAPIRequests.getAllStudentExams(exam)).length, { message: 'the student exam is generated on registration' }).toBe(1);
        const [studentExam]: StudentExam[] = await examAPIRequests.getAllStudentExams(exam);
        expect(studentExam.submitted).toBe(false);
        expect(studentExam.workingTime).toBe(3720);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        await examParticipation.makeTextExerciseSubmission(exercise.id!, textFixture);
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        await examParticipation.handInEarly();
        await examStartEnd.pressShowSummary();

        const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
        expect(summary.submitted).toBe(true);
        expect(summary.exercises).toHaveLength(1);
        await expectStoredAnswers(summary, [{ ...exercise, additionalData: { textFixture } }], { [ExerciseType.TEXT]: true });
    });
});
