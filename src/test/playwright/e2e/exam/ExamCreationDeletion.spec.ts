import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Course } from 'app/entities/course.model';
import { dayjsToString, generateUUID, trimDate } from '../../support/utils';
import dayjs from 'dayjs';
import { expect } from '@playwright/test';
import { Exam } from 'app/entities/exam.model';

/*
 * Common primitives
 */
const examData = {
    title: 'exam' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs().add(1, 'day'),
    endDate: dayjs().add(2, 'day'),
    numberOfExercisesInExam: 4,
    examMaxPoints: 40,
    startText: 'Exam start text',
    endText: 'Exam end text',
    confirmationStartText: 'Exam confirmation start text',
    confirmationEndText: 'Exam confirmation end text',
};

const editedExamData = {
    title: 'exam' + generateUUID(),
    visibleDate: dayjs(),
    startDate: dayjs().add(2, 'day'),
    endDate: dayjs().add(4, 'day'),
    numberOfExercisesInExam: 3,
    examMaxPoints: 30,
    startText: 'Edited exam start text',
    endText: 'Edited exam end text',
    confirmationStartText: 'Edited exam confirmation start text',
    confirmationEndText: 'Edited exam confirmation end text',
};

const dateFormat = 'MMM D, YYYY HH:mm';

test.describe('Exam creation/deletion', () => {
    let course: Course;
    let examId: number;

    test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
    });

    test('Creates an exam', async ({ navigationBar, courseManagement, examManagement, examCreation }) => {
        await navigationBar.openCourseManagement();
        await courseManagement.openExamsOfCourse(course.id!);

        await examManagement.createNewExam();
        await examCreation.setTitle(examData.title);
        await examCreation.setVisibleDate(examData.visibleDate);
        await examCreation.setStartDate(examData.startDate);
        await examCreation.setEndDate(examData.endDate);
        await examCreation.setNumberOfExercises(examData.numberOfExercisesInExam);
        await examCreation.setExamMaxPoints(examData.examMaxPoints);

        await examCreation.setStartText(examData.startText);
        await examCreation.setEndText(examData.endText);
        await examCreation.setConfirmationStartText(examData.confirmationStartText);
        await examCreation.setConfirmationEndText(examData.confirmationEndText);

        const response = await examCreation.submit();
        const exam: Exam = await response.json();
        examId = exam.id!;
        expect(response.status()).toBe(201);

        await expect(examManagement.getExamTitle()).toContainText(examData.title);
        await expect(examManagement.getExamVisibleDate()).toContainText(examData.visibleDate.format(dateFormat));
        await expect(examManagement.getExamStartDate()).toContainText(examData.startDate.format(dateFormat));
        await expect(examManagement.getExamEndDate()).toContainText(examData.endDate.format(dateFormat));
        await expect(examManagement.getExamNumberOfExercises()).toHaveText(examData.numberOfExercisesInExam.toString());
        await expect(examManagement.getExamMaxPoints()).toHaveText(examData.examMaxPoints.toString());
        await expect(examManagement.getExamStartText()).toContainText(examData.startText);
        await expect(examManagement.getExamEndText()).toContainText(examData.endText);
        await expect(examManagement.getExamConfirmationStartText()).toContainText(examData.confirmationStartText);
        await expect(examManagement.getExamConfirmationEndText()).toContainText(examData.confirmationEndText);
        await expect(examManagement.getExamWorkingTime()).toHaveText('1d 0h');
    });

    test.describe('Exam deletion', () => {
        test.beforeEach(async ({ examAPIRequests }) => {
            examData.title = 'exam' + generateUUID();
            const examConfig = {
                course,
                title: examData.title,
            };
            const examResponse = await examAPIRequests.createExam(examConfig);
            examId = examResponse.id!;
        });

        test('Deletes an existing exam', async ({ navigationBar, courseManagement, examManagement, examDetails }) => {
            await navigationBar.openCourseManagement();
            await courseManagement.openExamsOfCourse(course.id!);
            await examManagement.openExam(examId);
            await examDetails.deleteExam(examData.title);
            await expect(examManagement.getExamSelector(examData.title)).not.toBeVisible();
        });
    });

    test.describe('Edits an exam', () => {
        test.beforeEach(async ({ examAPIRequests }) => {
            examData.title = 'exam' + generateUUID();
            const examConfig = {
                course,
                title: examData.title,
            };
            const examResponse = await examAPIRequests.createExam(examConfig);
            examId = examResponse.id!;
        });

        test('Edits an existing exam', async ({ navigationBar, courseManagement, examManagement, examCreation }) => {
            await navigationBar.openCourseManagement();
            await courseManagement.openExamsOfCourse(course.id!);
            await examManagement.openExam(examId);

            await expect(examManagement.getExamTitle()).toContainText(examData.title);
            await examManagement.clickEdit();

            await examCreation.setTitle(editedExamData.title);
            await examCreation.setVisibleDate(editedExamData.visibleDate);
            await examCreation.setStartDate(editedExamData.startDate);
            await examCreation.setEndDate(editedExamData.endDate);
            await examCreation.setNumberOfExercises(editedExamData.numberOfExercisesInExam);
            await examCreation.setExamMaxPoints(editedExamData.examMaxPoints);

            await examCreation.setStartText(editedExamData.startText);
            await examCreation.setEndText(editedExamData.endText);
            await examCreation.setConfirmationStartText(editedExamData.confirmationStartText);
            await examCreation.setConfirmationEndText(editedExamData.confirmationEndText);

            const response = await examCreation.update();
            expect(response.status()).toBe(200);
            const exam = await response.json();

            examId = exam.id;
            expect(exam.testExam).toBeFalsy();
            expect(trimDate(exam.visibleDate)).toBe(trimDate(dayjsToString(editedExamData.visibleDate)));
            expect(trimDate(exam.startDate)).toBe(trimDate(dayjsToString(editedExamData.startDate)));
            expect(trimDate(exam.endDate)).toBe(trimDate(dayjsToString(editedExamData.endDate)));
            expect(exam.numberOfExercisesInExam).toBe(editedExamData.numberOfExercisesInExam);
            expect(exam.examMaxPoints).toBe(editedExamData.examMaxPoints);
            expect(exam.startText).toBe(editedExamData.startText);
            expect(exam.endText).toBe(editedExamData.endText);
            expect(exam.confirmationStartText).toBe(editedExamData.confirmationStartText);
            expect(exam.confirmationEndText).toBe(editedExamData.confirmationEndText);

            await expect(examManagement.getExamTitle()).toContainText(editedExamData.title);
            await expect(examManagement.getExamVisibleDate()).toContainText(dayjs(editedExamData.visibleDate).format(dateFormat));
            await expect(examManagement.getExamStartDate()).toContainText(dayjs(editedExamData.startDate).format(dateFormat));
            await expect(examManagement.getExamEndDate()).toContainText(dayjs(editedExamData.endDate).format(dateFormat));
            await expect(examManagement.getExamNumberOfExercises()).toHaveText(editedExamData.numberOfExercisesInExam.toString());
            await expect(examManagement.getExamMaxPoints()).toHaveText(editedExamData.examMaxPoints.toString());
            await expect(examManagement.getExamStartText()).toContainText(editedExamData.startText);
            await expect(examManagement.getExamEndText()).toContainText(editedExamData.endText);
            await expect(examManagement.getExamConfirmationStartText()).toContainText(editedExamData.confirmationStartText);
            await expect(examManagement.getExamConfirmationEndText()).toContainText(editedExamData.confirmationEndText);
            await expect(examManagement.getExamWorkingTime()).toHaveText('2d 0h');
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
