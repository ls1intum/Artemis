import { expect, Page } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, UserCredentials } from '../../support/users';
import { prepareRunningTextExam } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;

/**
 * Registering students and taking them off an exam again. Removing a student takes their access and their individual exam; the work they handed in
 * stays, unless the instructor explicitly asks to delete it, too. Students who are registered after the individual exams were generated get theirs
 * from "Generate missing individual exams" without touching the exams of the others.
 */
test.describe('Exam student registration', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    /** A student hands in a text, so that the exercise holds a submission of them. */
    async function handInText(page: Page, exerciseId: number, text: string) {
        const response = await page.request.post(`api/text/exercises/${exerciseId}/text-submissions`, { data: { text, submitted: true, language: 'ENGLISH' } });
        expect(response.status()).toBe(200);
    }

    /** The texts that students handed in for the exercise, as the instructor sees them. */
    async function handedInTexts(page: Page, exerciseId: number): Promise<string[]> {
        const response = await page.request.get(`api/text/exercises/${exerciseId}/text-submissions?submittedOnly=true`);
        expect(response.status()).toBe(200);
        return ((await response.json()) as { text?: string }[]).map((submission) => submission.text ?? '').sort();
    }

    async function removeStudent(page: Page, student: UserCredentials, options: { withParticipationsAndSubmission: boolean }) {
        const row = page.getByTestId('exam-students-table').locator('tbody tr', { hasText: new RegExp(`${student.username}(?!\\d)`) });
        await row.getByTestId('remove-student-button').click();
        const dialog = page.getByRole('dialog');
        if (options.withParticipationsAndSubmission) {
            await dialog.locator('#additional-check-0').check();
            // With the additional check the instructor has to type the login to confirm.
            await dialog.locator('#confirm-entity-name').fill(student.username);
        }
        const removed = page.waitForResponse((response) => response.url().includes(`/students/${student.username}`) && response.request().method() === 'DELETE');
        await dialog.getByTestId('delete-dialog-confirm-button').click();
        const response = await removed;
        expect(response.status()).toBe(200);
        expect(response.url()).toContain(`withParticipationsAndSubmission=${options.withParticipationsAndSubmission}`);
    }

    async function prepareExamWithThreeStudentsWhoHandedIn(
        login: (user: UserCredentials, url?: string) => Promise<void>,
        page: Page,
        requests: Parameters<typeof prepareRunningTextExam>[0],
        exercises: Parameters<typeof prepareRunningTextExam>[1],
    ) {
        await login(admin);
        const prepared = await prepareRunningTextExam(requests, exercises, { course, students: [studentOne, studentTwo, studentThree] });
        exam = prepared.exam;
        for (const student of [studentOne, studentTwo]) {
            await login(student);
            await handInText(page, prepared.exercise.id!, `Answer of ${student.username}`);
        }
        await login(admin);
        return prepared;
    }

    test('Removing a student takes their access and exam but keeps what they handed in', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
        const { exercise } = await prepareExamWithThreeStudentsWhoHandedIn(login, page, examAPIRequests, exerciseAPIRequests);
        const before = await exerciseAPIRequests.getExerciseParticipations(exercise.id!);
        expect(before, 'every registered student has a participation').toHaveLength(3);
        expect(await handedInTexts(page, exercise.id!)).toEqual([`Answer of ${studentOne.username}`, `Answer of ${studentTwo.username}`]);

        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        await expect(page.getByTestId('remove-student-button')).toHaveCount(3);
        await removeStudent(page, studentOne, { withParticipationsAndSubmission: false });
        await expect(page.getByTestId('remove-student-button')).toHaveCount(2);
        await expect(page.getByTestId('exam-students-table').locator('tbody tr', { hasText: new RegExp(`${studentOne.username}(?!\\d)`) })).toHaveCount(0);

        // The individual exam of the student is gone, the ones of the others are not, and the work of the student is still stored.
        await login(admin);
        expect(await examAPIRequests.getAllStudentExams(exam)).toHaveLength(2);
        const after = await exerciseAPIRequests.getExerciseParticipations(exercise.id!);
        expect(after, 'the participation of the removed student is kept').toHaveLength(3);
        expect(await handedInTexts(page, exercise.id!), 'and so are the submissions').toEqual([`Answer of ${studentOne.username}`, `Answer of ${studentTwo.username}`]);

        // The student can not get into the exam any more.
        await login(studentOne);
        const own = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/own-student-exam`);
        expect(own.status()).toBeGreaterThanOrEqual(400);
        expect(own.status()).toBeLessThan(500);
        // Another student still can.
        await login(studentTwo);
        expect((await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/own-student-exam`)).status()).toBe(200);
    });

    test('Removing a student with the additional check deletes their participation and submissions, too', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
        const { exercise } = await prepareExamWithThreeStudentsWhoHandedIn(login, page, examAPIRequests, exerciseAPIRequests);

        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        await expect(page.getByTestId('remove-student-button')).toHaveCount(3);
        await removeStudent(page, studentTwo, { withParticipationsAndSubmission: true });
        await expect(page.getByTestId('remove-student-button')).toHaveCount(2);

        await login(admin);
        expect(await examAPIRequests.getAllStudentExams(exam)).toHaveLength(2);
        const after = await exerciseAPIRequests.getExerciseParticipations(exercise.id!);
        expect(after, 'the participation of the removed student is deleted with their submission').toHaveLength(2);
        expect(after.map((participation) => participation.studentLogin).sort()).toEqual([studentOne.username, studentThree.username]);
        expect(await handedInTexts(page, exercise.id!), 'only the submission of the removed student is deleted').toEqual([`Answer of ${studentOne.username}`]);
    });

    test('Individual exams that are missing are generated without touching the existing ones', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({ course, visibleDate: dayjs().subtract(1, 'day'), startDate: dayjs().add(2, 'days'), endDate: dayjs().add(3, 'days') });
        const exerciseGroup: ExerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        for (const student of [studentOne, studentTwo]) {
            await examAPIRequests.registerStudentForExam(exam, student);
        }
        await examAPIRequests.generateMissingIndividualExams(exam);
        const existing: StudentExam[] = await examAPIRequests.getAllStudentExams(exam);
        expect(existing).toHaveLength(2);

        // Two more students are registered afterwards, and the exam has none for them.
        for (const student of [studentThree, studentFour]) {
            await examAPIRequests.registerStudentForExam(exam, student);
        }
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        await page.getByRole('button', { name: 'Individual exams', exact: true }).click();
        const generated = page.waitForResponse((response) => response.url().includes('generate-missing-student-exams'));
        await page.locator('[data-testid="exam-students-menu-item"]', { hasText: 'Generate missing individual exams' }).last().click();
        expect((await generated).status()).toBe(200);

        await login(admin);
        const all: StudentExam[] = await examAPIRequests.getAllStudentExams(exam);
        expect(all, 'one individual exam per registered student').toHaveLength(4);
        for (const studentExam of existing) {
            expect(
                all.map((candidate) => candidate.id),
                `the existing individual exam ${studentExam.id} is kept`,
            ).toContain(studentExam.id);
        }
        // Nothing is left to generate: the menu entry is disabled.
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        await page.getByRole('button', { name: 'Individual exams', exact: true }).click();
        await expect(page.locator('[data-testid="exam-students-menu-entry"]', { hasText: 'Generate missing individual exams' })).toHaveAttribute('aria-disabled', 'true');
    });
});
