import dayjs from 'dayjs';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

import { admin } from '../../../support/users';
import { dayjsToString, generateUUID, trimDate } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

// Common primitives
const examData = {
    title: 'exam' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs().add(1, 'day'),
    endDate: dayjs().add(2, 'day'),
    workingTime: 5,
    numberOfExercises: 4,
    maxPoints: 40,
    startText: 'Exam start text',
    endText: 'Exam end text',
    confirmationStartText: 'Exam confirmation start text',
    confirmationEndText: 'Exam confirmation end text',
};

test.describe('Test Exam creation/deletion', () => {
    let course: Course;
    let exam: Exam;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin, '/');
        course = await courseManagementAPIRequests.createCourse();
    });

    test('Creates a test exam', async ({ page, navigationBar, courseManagement, examManagement, examCreation }) => {
        await navigationBar.openCourseManagement();
        await courseManagement.openExamsOfCourse(course.id!);

        await examManagement.createNewExam();
        await examCreation.setTitle(examData.title);
        await examCreation.setTestMode();
        await examCreation.setVisibleDate(examData.visibleDate);
        await examCreation.setStartDate(examData.startDate);
        await examCreation.setEndDate(examData.endDate);
        await examCreation.setWorkingTime(examData.workingTime);
        await examCreation.setNumberOfExercises(examData.numberOfExercises);
        await examCreation.setExamMaxPoints(examData.maxPoints);

        await examCreation.setStartText(examData.startText);
        await examCreation.setEndText(examData.endText);
        await examCreation.setConfirmationStartText(examData.confirmationStartText);
        await examCreation.setConfirmationEndText(examData.confirmationEndText);

        const examResponse = await examCreation.submit();
        const exam = await examResponse.json();
        expect(examResponse.status()).toBe(201);
        expect(exam.title).toBe(examData.title);
        expect(exam.testExam).toBe(true);
        expect(trimDate(exam.visibleDate)).toBe(trimDate(dayjsToString(examData.visibleDate)));
        expect(trimDate(exam.startDate)).toBe(trimDate(dayjsToString(examData.startDate)));
        expect(trimDate(exam.endDate)).toBe(trimDate(dayjsToString(examData.endDate)));
        expect(exam.workingTime).toBe(examData.workingTime * 60);
        expect(exam.numberOfExercisesInExam).toBe(examData.numberOfExercises);
        expect(exam.examMaxPoints).toBe(examData.maxPoints);
        expect(exam.startText).toBe(examData.startText);
        expect(exam.endText).toBe(examData.endText);
        expect(exam.confirmationStartText).toBe(examData.confirmationStartText);
        expect(exam.confirmationEndText).toBe(examData.confirmationEndText);
        await page.waitForURL(`**/exams/${exam.id}**`);
        await expect(examManagement.getExamTitle().getByText(examData.title)).toBeVisible();
    });

    test.describe('Test exam deletion', () => {
        test.beforeEach(async ({ examAPIRequests }) => {
            examData.title = 'exam' + generateUUID();
            const examConfig = {
                course,
                title: examData.title,
                testExam: true,
            };
            exam = await examAPIRequests.createExam(examConfig);
        });

        test('Deletes an existing test exam', async ({ navigationBar, courseManagement, examManagement, examDetails }) => {
            await navigationBar.openCourseManagement();
            await courseManagement.openExamsOfCourse(course.id!);
            await expect(examManagement.getExamSelector(examData.title)).toBeVisible();
            await examManagement.openExam(exam.id!);
            await examDetails.deleteExam(examData.title);
            await expect(examManagement.getExamSelector(examData.title)).not.toBeAttached();
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
