import { expect, Page } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;

type PreparationStatus = { finished: number; failed: number; overall: number; participationCount: number } | null;

/**
 * Before an exam the exercises are prepared for every individual exam: the participations and submissions of the students are created. The
 * instructor follows the preparation in the list of students: how many individual exams are prepared, how many failed, and whether everything is
 * ready. A change of the registered students makes the preparation incomplete again.
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

    test('The preparation is tracked, reported in the student list and incomplete again when a student is added', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
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
        expect(await exerciseAPIRequests.getExerciseParticipations(exercises[0].id!)).toHaveLength(0);
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
        for (const exercise of exercises) {
            expect((await exerciseAPIRequests.getExerciseParticipations(exercise.id!)).map((participation) => participation.studentLogin).sort()).toEqual(
                [studentOne, studentTwo, studentThree].map((student) => student.username).sort(),
            );
        }

        // The list of students reports that everything is ready.
        await page.reload();
        popover = await openStatusPopover(page);
        await expect(popover).toContainText('All exercises are prepared for all individual exams');
        await expect(popover).toContainText('3 / 3');
        await page.keyboard.press('Escape');

        // A student who is registered afterwards has no individual exam and nothing prepared: the preparation is incomplete again, and the old status is gone.
        await login(admin);
        await examAPIRequests.registerStudentForExam(exam, studentFour);
        await examAPIRequests.generateMissingIndividualExams(exam);
        expect(await status(page), 'the status of the earlier preparation is dropped').toBeNull();
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        popover = await openStatusPopover(page);
        await expect(popover).toContainText('Not all exercises are prepared for all individual exams');
        await page.keyboard.press('Escape');

        // Preparing again ends with all four individual exams prepared, and the students prepared before keep their participations.
        await login(admin);
        await examAPIRequests.prepareExerciseStartForExam(exam);
        await expect.poll(async () => (await status(page))?.finished, { timeout: 60_000 }).toBe(4);
        expect((await status(page))!.failed).toBe(0);
        for (const exercise of exercises) {
            expect(await exerciseAPIRequests.getExerciseParticipations(exercise.id!), 'one participation per student, none twice').toHaveLength(4);
        }
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
