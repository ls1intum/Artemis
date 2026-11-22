import { expect, Page } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;

type PreparationStatus = { finished: number; failed: number; overall: number; participationCount: number } | null;

/**
 * Before an exam the exercises are prepared for every individual exam: the participations and submissions of the students are created. The
 * instructor follows the preparation in the list of students: how many individual exams are prepared, how many failed, and whether everything is
 * ready. Students registered after preparation receive prepared individual exams without replacing the existing work.
 */
test.describe('Exam exercise preparation', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    async function status(page: Page): Promise<PreparationStatus> {
        const response = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/start-exercises/status`);
        expect(response.status()).toBe(200);
        const body = await response.text();
        return body ? (JSON.parse(body) as PreparationStatus) : null;
    }

    async function openStatusPopover(page: Page) {
        await page.getByRole('button', { name: 'Individual exams status', exact: true }).click();
        return page.getByRole('dialog', { name: 'Individual exams status' });
    }

    test('Preparation is tracked and late registration prepares the new student while preserving existing work', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({
            course,
            visibleDate: dayjs().subtract(1, 'day'),
            startDate: dayjs().add(2, 'days'),
            endDate: dayjs().add(3, 'days'),
            numberOfExercisesInExam: 2,
            examMaxPoints: 20,
        });
        const exercises = [];
        for (let index = 0; index < 2; index++) {
            const group = await examAPIRequests.addExerciseGroupForExam(exam);
            exercises.push(await exerciseAPIRequests.createTextExercise({ exerciseGroup: group }));
        }
        for (const student of [studentOne, studentTwo, studentThree]) {
            await examAPIRequests.registerStudentForExam(exam, student);
        }
        await examAPIRequests.generateMissingIndividualExams(exam);

        // Nothing is prepared yet: there is no status, and no participation exists.
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        expect(await status(page)).toBeNull();
        expect(await exerciseAPIRequests.getExerciseParticipations(exercises[0].id!, 'exam')).toHaveLength(0);
        let popover = await openStatusPopover(page);
        await expect(popover).toContainText('Not all exercises are prepared for all individual exams');
        await page.keyboard.press('Escape');

        // The instructor prepares the exercises; the server counts the individual exams it has finished and ends with all of them.
        await page.getByRole('button', { name: 'Individual exams', exact: true }).click();
        const started = page.waitForResponse((response) => response.url().includes('start-exercises') && response.request().method() === 'POST');
        await page.locator('[data-testid="exam-students-menu-item"]', { hasText: 'Prepare exercise start' }).last().click();
        expect((await started).status()).toBeLessThan(300);
        await expect.poll(async () => (await status(page))?.finished, { message: 'all three individual exams are prepared', timeout: 60_000 }).toBe(3);
        const done = (await status(page))!;
        expect(done.overall).toBe(3);
        expect(done.failed, 'no preparation failed').toBe(0);
        expect(done.participationCount, 'one participation per student and exercise').toBe(6);
        const existingExams: StudentExam[] = await examAPIRequests.getAllStudentExams(exam);
        expect(existingExams).toHaveLength(3);
        const existingParticipations = new Map<number, { participationId: number; studentLogin?: string }[]>();
        for (const exercise of exercises) {
            const participations = await exerciseAPIRequests.getExerciseParticipations(exercise.id!, 'exam');
            existingParticipations.set(
                exercise.id!,
                participations.map((participation) => ({ participationId: participation.participationId, studentLogin: participation.studentLogin })),
            );
            expect(participations.map((participation) => participation.studentLogin).sort()).toEqual(
                [studentOne, studentTwo, studentThree].map((student) => student.username).sort(),
            );
        }

        // The list of students reports that everything is ready.
        await page.reload();
        popover = await openStatusPopover(page);
        await expect(popover).toContainText('All exercises are prepared for all individual exams');
        await expect(popover).toContainText('3 / 3');
        await page.keyboard.press('Escape');

        // Late registration generates and prepares the new student's exam automatically.
        await login(admin);
        await examAPIRequests.registerStudentForExam(exam, studentFour);
        await expect.poll(async () => (await status(page))?.finished, { message: 'late registration prepares all four individual exams', timeout: 60_000 }).toBe(4);
        const afterRegistration = (await status(page))!;
        expect(afterRegistration.overall).toBe(4);
        expect(afterRegistration.failed).toBe(0);
        expect(afterRegistration.participationCount, 'only the new student needs two new participations').toBe(2);
        const allExams: StudentExam[] = await examAPIRequests.getAllStudentExams(exam);
        expect(allExams).toHaveLength(4);
        expect(allExams.map((studentExam) => studentExam.id)).toEqual(expect.arrayContaining(existingExams.map((studentExam) => studentExam.id)));
        expect(new Set(allExams.map((studentExam) => studentExam.id)).size).toBe(4);
        for (const exercise of exercises) {
            const participations = await exerciseAPIRequests.getExerciseParticipations(exercise.id!, 'exam');
            expect(participations, 'one participation per student, none twice').toHaveLength(4);
            expect(participations.map((participation) => participation.studentLogin).sort()).toEqual(
                [studentOne, studentTwo, studentThree, studentFour].map((student) => student.username).sort(),
            );
            expect(participations.map((participation) => ({ participationId: participation.participationId, studentLogin: participation.studentLogin }))).toEqual(
                expect.arrayContaining(existingParticipations.get(exercise.id!)!),
            );
        }

        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        await expect(page.getByTestId('remove-student-button')).toHaveCount(4);
        for (const student of [studentOne, studentTwo, studentThree, studentFour]) {
            await expect(page.getByTestId('exam-students-table').getByText(student.username, { exact: true })).toBeVisible();
        }
        popover = await openStatusPopover(page);
        await expect(popover).toContainText('All exercises are prepared for all individual exams');
        await expect(popover).toContainText('4 / 4');
        await page.keyboard.press('Escape');
        await page.getByRole('button', { name: 'Individual exams', exact: true }).click();
        await expect(page.getByTestId('exam-students-menu-entry').filter({ hasText: 'Generate missing individual exams' })).toHaveAttribute('aria-disabled', 'true');
    });

    test('The list of students shows failed preparations and does not report the exam as ready', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({ course, visibleDate: dayjs().subtract(1, 'day'), startDate: dayjs().add(2, 'days'), endDate: dayjs().add(3, 'days') });
        const group = await examAPIRequests.addExerciseGroupForExam(exam);
        await exerciseAPIRequests.createTextExercise({ exerciseGroup: group });
        for (const student of [studentOne, studentTwo, studentThree]) {
            await examAPIRequests.registerStudentForExam(exam, student);
        }
        await examAPIRequests.generateMissingIndividualExams(exam);

        // A preparation fails for one of the three individual exams. The failure itself can not be provoked on a healthy server, so the answer of the
        // server is given for it; what is under test is what the instructor is told.
        await page.route('**/student-exams/start-exercises/status', (route) =>
            route.fulfill({ json: { finished: 2, failed: 1, overall: 3, participationCount: 2, startedAt: new Date().toISOString() } }),
        );
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        const popover = await openStatusPopover(page);
        await expect(popover).toContainText('Not all exercises are prepared for all individual exams');
        await expect(popover).toContainText('1 failed');
        await expect(popover).toContainText('2 / 3');
        await expect(popover).not.toContainText('All exercises are prepared for all individual exams');
    });
});
