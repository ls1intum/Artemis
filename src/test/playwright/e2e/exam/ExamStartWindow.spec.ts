import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { asAdmin } from '../../support/utils';
import { ModalDialogBox } from '../../support/pageobjects/exam/ModalDialogBox';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/**
 * A student can already confirm and get ready for an exam in the last five minutes before it starts: the student then waits on a screen that
 * counts down to the start, and the exam begins by itself at the planned time without the student doing anything. Before that window, starting
 * is not possible. The exam is moved to a start shortly from now once the exercises are prepared, so the test does not depend on how long its
 * setup takes.
 */
test.describe('Exam start window', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('The student waits for the planned start and is taken into the exam when it begins', async ({
        browser,
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examStartEnd,
        examNavigation,
    }) => {
        test.setTimeout(240_000);
        await login(admin);
        exam = await examAPIRequests.createExam({
            course,
            visibleDate: dayjs().subtract(1, 'day'),
            startDate: dayjs().add(2, 'days'),
            endDate: dayjs().add(3, 'days'),
        });
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        // Two days before the start the exam can not be started, even though the student confirmed and entered the name.
        await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
        await expect(page.getByTestId('start-exam')).toBeDisabled();
        await page.locator('#confirmBox').check();
        await examStartEnd.enterFirstnameLastname();
        await expect(page.getByTestId('start-exam')).toBeDisabled();

        // The exam is moved to start in a minute and a half: from now on the student is inside the five minutes before the start.
        await asAdmin(browser, (adminExamRequests) => adminExamRequests.rescheduleExam(exam, 90, 2 * 60 * 60));
        await page.reload();
        await expect(page.getByTestId('start-exam')).toBeDisabled();
        await page.locator('#confirmBox').check();
        await examStartEnd.enterFirstnameLastname();
        await expect(page.getByTestId('start-exam')).toBeEnabled();
        const loaded = page.waitForResponse((response) => /\/student-exams\/\d+\/conduction$/.test(response.url()));
        await page.getByTestId('start-exam').click();
        expect((await loaded).status()).toBe(200);

        // The student waits: the screen names the exam and counts down, and nothing of the exam itself is there yet.
        await expect(page.getByText(`Please wait until the exam "${exam.title}" begins.`)).toBeVisible();
        await expect(page.getByText('Time until planned start:')).toBeVisible();
        await expect(page.getByTestId('hand-in-early')).toHaveCount(0);

        // At the planned start the exam begins by itself.
        await expect(page.getByTestId('hand-in-early')).toBeVisible({ timeout: 150_000 });
        await expect(page.getByTestId('exam-bar-title')).toContainText(exam.title!);
        await expect(page.getByTestId('displayTime')).toContainText('1h');
        // Moving the exam changed its duration, so the student is told about the new working time as soon as the exam is running.
        const notification = new ModalDialogBox(page);
        await notification.checkDialogType('Working Time Update');
        await notification.closeDialog();
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        await expect(page.locator('#text-editor')).toBeEnabled();
    });
});
