import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { generateUUID, readResponseJson } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { admin, instructor } from '../../support/users';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;

/**
 * The exam form only lets an instructor save an exam whose configuration is consistent. Each rule is checked on the same form: the form is
 * valid, one field is made invalid so that saving is refused, and the field is corrected so that saving is possible again. The last step
 * really saves the exam, which shows that the form works after all these edits.
 */
test.describe('Exam form validation', { tag: '@fast' }, () => {
    let createdExam: Exam | undefined;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        if (createdExam) {
            await login(admin);
            await examAPIRequests.deleteExam(createdExam);
            createdExam = undefined;
        }
    });

    test('The form refuses an inconsistent configuration and accepts it once it is consistent', async ({ page, login, examCreation }) => {
        const title = 'exam' + generateUUID();
        const visibleDate = dayjs();
        const startDate = dayjs().add(1, 'hour');
        const endDate = dayjs().add(2, 'hours');
        const save = page.locator('#save-exam');

        await login(instructor, `/course-management/${course.id}/exams/new`);
        await examCreation.setTitle(title);
        await examCreation.setVisibleDate(visibleDate);
        await examCreation.setStartDate(startDate);
        await examCreation.setEndDate(endDate);
        await examCreation.setNumberOfExercises(4);
        await examCreation.setExamMaxPoints(40);
        await expect(save, 'a consistent configuration can be saved').toBeEnabled();

        // The title must not be empty, and a title of blanks counts as empty.
        await examCreation.setTitle('   ');
        await expect(save).toBeDisabled();
        await examCreation.setTitle(title);
        await expect(save).toBeEnabled();

        // The exam can not end before it starts.
        await examCreation.setEndDate(startDate.subtract(1, 'hour'));
        await expect(save).toBeDisabled();
        await examCreation.setEndDate(endDate);
        await expect(save).toBeEnabled();

        // The number of exercises is between 1 and 100.
        for (const invalidNumber of [0, 101]) {
            await examCreation.setNumberOfExercises(invalidNumber);
            await expect(save, `${invalidNumber} exercises`).toBeDisabled();
        }
        await examCreation.setNumberOfExercises(4);
        await expect(save).toBeEnabled();

        // The exam is worth points.
        await examCreation.setExamMaxPoints(0);
        await expect(save).toBeDisabled();
        await examCreation.setExamMaxPoints(40);
        await expect(save).toBeEnabled();

        // The results can only be published after the end, the review starts after the publication and ends after it started.
        await examCreation.setPublishResultsDate(endDate.subtract(30, 'minutes'));
        await expect(save, 'results published before the exam ends').toBeDisabled();
        await examCreation.setPublishResultsDate(endDate.add(1, 'hour'));
        await expect(save).toBeEnabled();
        await examCreation.setStudentReviewStartDate(endDate.add(30, 'minutes'));
        await expect(save, 'review starts before the results are published').toBeDisabled();
        await examCreation.setStudentReviewStartDate(endDate.add(2, 'hours'));
        await expect(save, 'a review that starts has to end as well').toBeDisabled();
        await examCreation.setStudentReviewEndDate(endDate.add(1, 'hour'));
        await expect(save, 'review ends before it starts').toBeDisabled();
        await examCreation.setStudentReviewEndDate(endDate.add(3, 'hours'));
        await expect(save).toBeEnabled();

        // Saving works, and the exam holds what the form finally said.
        const response = await examCreation.submit();
        expect(response.status(), await response.text()).toBe(201);
        createdExam = await readResponseJson<Exam>(response);
        expect(createdExam.title).toBe(title);
        expect(createdExam.numberOfExercisesInExam).toBe(4);
        expect(createdExam.examMaxPoints).toBe(40);
        expect(dayjs(createdExam.publishResultsDate as any).isAfter(dayjs(createdExam.endDate as any))).toBe(true);
        expect(dayjs(createdExam.examStudentReviewEnd as any).isAfter(dayjs(createdExam.examStudentReviewStart as any))).toBe(true);
    });
    test('The form of a test exam only accepts a working time that fits into the time the exam is open', async ({ page, login, examCreation }) => {
        const title = 'exam' + generateUUID();
        const save = page.locator('#save-exam');

        await login(instructor, `/course-management/${course.id}/exams/new`);
        await examCreation.setTitle(title);
        await examCreation.setTestMode();
        await examCreation.setVisibleDate(dayjs());
        // The exam is open for two hours.
        await examCreation.setStartDate(dayjs().add(1, 'hour'));
        await examCreation.setEndDate(dayjs().add(3, 'hours'));
        await examCreation.setNumberOfExercises(2);
        await examCreation.setExamMaxPoints(20);

        // An attempt can not be longer than the exam is open, and it needs some time.
        for (const invalidMinutes of [121, 0]) {
            await examCreation.setWorkingTime(invalidMinutes);
            await expect(save, `a working time of ${invalidMinutes} minutes`).toBeDisabled();
        }
        await examCreation.setWorkingTime(120);
        await expect(save, 'an attempt as long as the exam is open').toBeEnabled();
        await examCreation.setWorkingTime(45);
        await expect(save).toBeEnabled();

        // The exam is saved as a test exam with the attempt length that was chosen.
        const response = await examCreation.submit();
        expect(response.status(), await response.text()).toBe(201);
        createdExam = await readResponseJson<Exam>(response);
        expect(createdExam.testExam).toBe(true);
        expect(createdExam.workingTime).toBe(45 * 60);
        expect(createdExam.numberOfCorrectionRoundsInExam, 'a test exam has no correction rounds').toBe(0);
    });
});
