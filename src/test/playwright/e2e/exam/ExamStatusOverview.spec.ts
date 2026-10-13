import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentTwo } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;

/**
 * The exam list shows a status for each exam that guides the instructor through it: the preparation (exercises, registered students, generated
 * individual exams, prepared exercise start), the conduction (planned, running, finished, with the number of started and submitted exams) and the
 * correction. The status follows the exam through its life, so it is checked at each stage on the exam of this test.
 */
test.describe('Exam status in the exam list', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('The status follows the exam from preparation through conduction to correction', async ({ page, login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({ course, visibleDate: dayjs().subtract(1, 'day'), startDate: dayjs().add(2, 'days'), endDate: dayjs().add(3, 'days') });
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        await exerciseAPIRequests.createTextExercise({ exerciseGroup });

        // The status of this exam in the list.
        const status = () => page.locator(`[data-testid="exam-status"][data-exam-id="${exam.id}"]`);
        const preparation = () => status().getByTestId('exam-status-preparation');
        const conduction = () => status().getByTestId('exam-status-conduction');
        const correction = () => status().getByTestId('exam-status-correction');
        const open = async () => {
            await login(instructor, `/course-management/${course.id}/exams`);
            await expect(status()).toBeVisible();
        };

        // Stage 1: an exercise is configured, but nobody is registered: the preparation is open, the exam is planned.
        await open();
        await expect(preparation()).toHaveAttribute('data-state', 'open');
        await expect(conduction()).toHaveAttribute('data-state', 'PLANNED');
        await expect(correction()).toHaveAttribute('data-state', 'unset');
        await expect(status().getByTestId('exam-status-preparation-details')).toContainText('Register students');

        // Stage 2: students are registered, their individual exams are generated and the exercises are prepared: the preparation is finished.
        await login(admin);
        for (const student of [studentOne, studentTwo]) {
            await examAPIRequests.registerStudentForExam(exam, student);
        }
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
        await open();
        await expect(preparation()).toHaveAttribute('data-state', 'finished');
        await expect(conduction()).toHaveAttribute('data-state', 'PLANNED');
        await expect(status().getByTestId('exam-status-preparation-details')).toContainText('2 of 2');

        // Stage 3: the exam is running, one of the two students started it.
        await examAPIRequests.rescheduleExam(exam, -60, 2 * 60 * 60);
        await login(studentOne);
        await examAPIRequests.getOwnStudentExamForConduction(exam);
        await open();
        await expect(conduction()).toHaveAttribute('data-state', 'RUNNING');
        await expect(status().getByTestId('exam-status-conduction-details')).toContainText(/Started exams\s*:\s*1\s*\(50\s*%\)/);
        await expect(status().getByTestId('exam-status-conduction-details')).toContainText(/Submitted exams\s*:\s*0\s*\(0\s*%\)/);

        // Stage 4: the exam is over, and the correction has begun with the results published after the exam.
        await login(admin);
        await examAPIRequests.concludeExam(exam);
        await open();
        await expect(conduction()).toHaveAttribute('data-state', 'FINISHED');
        await expect(correction()).toHaveAttribute('data-state', /planned|running/);
    });
});
