import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { Course } from 'app/entities/course.model';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { Fixtures } from '../../fixtures/fixtures';

test.describe('Exam date verification', () => {
    let course: Course;
    let examTitle: string;

    test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        examTitle = 'exam' + generateUUID();
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
    });

    test.describe('Exam timing', () => {
        test('Does not show exam before visible date', async ({ page, login, examAPIRequests }) => {
            const examConfig = {
                course,
                title: examTitle,
                visibleDate: dayjs().add(1, 'day'),
                startDate: dayjs().add(2, 'days'),
                endDate: dayjs().add(3, 'days'),
            };
            await examAPIRequests.createExam(examConfig);
            await login(studentOne);
            await page.goto(`/courses`);
            await expect(page.getByText(examTitle)).not.toBeVisible();
            await page.goto(`/courses/${course.id}`);
            await expect(page.getByText(examTitle)).not.toBeVisible();
        });

        test('Shows after visible date', async ({ login, examAPIRequests, page, courseOverview }) => {
            const examConfig = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(5, 'days'),
                startDate: dayjs().add(2, 'days'),
                endDate: dayjs().add(3, 'days'),
            };
            const exam = await examAPIRequests.createExam(examConfig);
            await examAPIRequests.registerStudentForExam(exam, studentOne);
            await login(studentOne);
            await page.goto(`/courses/${course.id}`);
            await courseOverview.openExamsTab();
            await page.waitForURL(`**/exams/${exam.id}`);
        });

        test('Student can start after start Date', async ({
            page,
            login,
            examAPIRequests,
            exerciseAPIRequests,
            courseOverview,
            examStartEnd,
            examNavigation,
            textExerciseEditor,
        }) => {
            const examConfig = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(3, 'days'),
                startDate: dayjs().subtract(2, 'days'),
                endDate: dayjs().add(3, 'days'),
            };
            const exam = await examAPIRequests.createExam(examConfig);
            const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
            const exercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup });
            await examAPIRequests.registerStudentForExam(exam, studentOne);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
            await login(studentOne);
            await page.goto(`/courses/${course.id}/exams`);
            await courseOverview.openExamsTab();
            await courseOverview.openExam(exam.title!);
            await page.waitForURL(`**/exams/${exam.id}`);
            await expect(page.getByText(exam.title!).first()).toBeVisible();
            await examStartEnd.startExam();

            await page.hover('.fa-hourglass-half');
            await expect(page.getByText('Exercise not started')).toBeVisible();
            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            const submission = await Fixtures.get('loremIpsum-short.txt');
            await textExerciseEditor.typeSubmission(exercise.id!, submission!);

            await page.hover('.fa-save-warning');
            await expect(page.getByText('Exercise not saved')).toBeVisible();
            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);

            await page.hover('.fa-save-success');
            await expect(page.getByText('Exercise saved')).toBeVisible();
        });

        test('Exam ends after end time', async ({ page, login, examAPIRequests, exerciseAPIRequests, examStartEnd, examNavigation, textExerciseEditor, examParticipation }) => {
            const examEnd = dayjs().add(10, 'seconds');
            const examConfig = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(3, 'days'),
                startDate: dayjs().subtract(2, 'days'),
                endDate: examEnd,
            };
            const exam = await examAPIRequests.createExam(examConfig);
            const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
            const exercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup });
            await examAPIRequests.registerStudentForExam(exam, studentOne);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
            await login(studentOne);
            await page.goto(`/courses/${course.id}/exams/${exam.id}`);
            await page.waitForURL(`**/exams/${exam.id}`);
            await expect(page.getByText(exam.title!).first()).toBeVisible();
            await examStartEnd.startExam();

            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            const submissionText = await Fixtures.get('loremIpsum-short.txt');
            await textExerciseEditor.typeSubmission(exercise.id!, submissionText!);
            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            if (examEnd.isAfter(dayjs())) {
                await page.waitForTimeout(examEnd.diff(dayjs()));
            }
            await examParticipation.checkExamFinishedTitle(exam.title!);
            await examStartEnd.finishExam();
        });
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
