import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { Fixtures } from '../../fixtures/fixtures';
import { SEED_COURSES } from '../../support/seedData';

const course = { id: SEED_COURSES.examManagement.id } as any;

test.describe('Exam date verification', { tag: '@fast' }, () => {
    let examTitle: string;
    let exam: any;

    test.beforeEach(async ({ login }) => {
        await login(admin);
        examTitle = 'exam' + generateUUID();
    });

    test.afterEach('Delete exam', async ({ examAPIRequests }) => {
        if (exam) {
            await examAPIRequests.deleteExam(exam);
            exam = undefined;
        }
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
            exam = await examAPIRequests.createExam(examConfig);
            await login(studentOne);
            await page.goto(`/courses`);
            await page.waitForLoadState('networkidle');
            await expect(page.getByText(examTitle)).not.toBeVisible();
            await page.goto(`/courses/${course.id}`);
            await page.waitForLoadState('networkidle');
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
            exam = await examAPIRequests.createExam(examConfig);
            await examAPIRequests.registerStudentForExam(exam, studentOne);
            await login(studentOne);
            await page.goto(`/courses/${course.id}`);
            await page.waitForLoadState('networkidle');
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
            exam = await examAPIRequests.createExam(examConfig);
            const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
            const exercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup });
            await examAPIRequests.registerStudentForExam(exam, studentOne);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
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
            // nth(0) is the button, nth(1) is the ngtooltip, which is tested
            await expect(page.getByText('Exercise saved').nth(1)).toBeVisible();
        });

        test('Exam ends after end time', async ({ page, login, examAPIRequests, exerciseAPIRequests, examStartEnd, examNavigation, textExerciseEditor, examParticipation }) => {
            const examEnd = dayjs().add(15, 'seconds');
            const examConfig = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(3, 'days'),
                startDate: dayjs().subtract(2, 'days'),
                endDate: examEnd,
            };
            exam = await examAPIRequests.createExam(examConfig);
            const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
            const exercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup });
            await examAPIRequests.registerStudentForExam(exam, studentOne);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
            await login(studentOne);
            await page.goto(`/courses/${course.id}/exams/${exam.id}`);
            await page.waitForLoadState('networkidle');
            await page.waitForURL(`**/exams/${exam.id}`);
            await expect(page.getByText(exam.title!).first()).toBeVisible();
            await examStartEnd.startExam();

            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            const submissionText = await Fixtures.get('loremIpsum-short.txt');
            await textExerciseEditor.typeSubmission(exercise.id!, submissionText!);
            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            if (examEnd.isAfter(dayjs())) {
                await page.waitForTimeout(examEnd.diff(dayjs()) + 2000);
            }
            await examParticipation.checkExamFinishedTitle(exam.title!);
            await examStartEnd.finishExam();
        });
    });
});
