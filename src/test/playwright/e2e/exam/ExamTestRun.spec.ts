import dayjs from 'dayjs';

import { Exam } from 'app/exam/shared/entities/exam.model';

import cBuildErrorSubmission from '../../fixtures/exercise/programming/c/build_error/submission.json';
import { Exercise, ExerciseType, ProgrammingLanguage } from '../../support/constants';
import { admin, instructor } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { test } from '../../support/fixtures';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { expect } from '@playwright/test';
import { Commands } from '../../support/commands';
import { SEED_COURSES } from '../../support/seedData';

// Common primitives
const textFixture = 'loremIpsum-short.txt';
const examTitle = 'exam' + generateUUID();
const course = { id: SEED_COURSES.examTestRun.id } as any;

test.describe('Exam test run', { tag: '@slow' }, () => {
    let exam: Exam;
    let exerciseArray: Array<Exercise> = [];

    test.beforeEach('Create exam', async ({ login, examExerciseGroupCreation, examAPIRequests }) => {
        await login(admin);
        const examConfig = {
            course,
            title: examTitle,
            visibleDate: dayjs().subtract(3, 'days'),
            startDate: dayjs().add(1, 'days'),
            endDate: dayjs().add(3, 'days'),
            examMaxPoints: 40,
            numberOfExercisesInExam: 4,
        };
        exam = await examAPIRequests.createExam(examConfig);
        exerciseArray = [
            await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }),
            await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, {
                submission: cBuildErrorSubmission,
                practiceMode: true,
                skipBuildResultCheck: true,
                programmingLanguage: ProgrammingLanguage.C,
            }),
            await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 }),
            await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING),
        ];
    });

    test('Creates a test run', async ({ login, page, examManagement, examTestRun }) => {
        await login(instructor);

        const minutes = 40;
        const seconds = 30;

        await page.goto(`/course-management/${course.id}/exams/${exam.id}`);
        await examManagement.openTestRun();
        await examTestRun.createTestRun();
        await examTestRun.setWorkingTimeMinutes(minutes);
        await examTestRun.setWorkingTimeSeconds(seconds);
        const testRunResponse = await examTestRun.confirmTestRun();
        const testRun: StudentExam = await testRunResponse.json();
        expect(testRunResponse.status()).toBe(200);
        expect(testRun.testRun).toBe(true);
        expect(testRun.submitted).toBe(false);
        expect(testRun.workingTime).toBe(minutes * 60 + seconds);

        await expect(examTestRun.getWorkingTime(testRun.id!).filter({ hasText: `${minutes}min ${seconds}s` })).toBeVisible();
        await expect(examTestRun.getStarted(testRun.id!).filter({ hasText: 'No' })).toBeVisible();
        await expect(examTestRun.getSubmitted(testRun.id!).filter({ hasText: 'No' })).toBeVisible();
    });

    test.describe('Manage a test run', () => {
        test('Changes test run working time', async ({ login, courseManagementAPIRequests, examTestRun }) => {
            await login(instructor);
            const testRun = await courseManagementAPIRequests.createExamTestRun(exam, exerciseArray);

            const hour = 1;
            const minutes = 20;
            const seconds = 45;

            await login(instructor);
            await examTestRun.openTestRunPage(course, exam);
            await examTestRun.changeWorkingTime(testRun.id!);
            await examTestRun.setWorkingTimeHours(hour);
            await examTestRun.setWorkingTimeMinutes(minutes);
            await examTestRun.setWorkingTimeSeconds(seconds);
            const testRunResponse = await examTestRun.saveTestRun();
            const updatedTestRun: StudentExam = await testRunResponse.json();

            expect(testRunResponse.status()).toBe(200);
            expect(updatedTestRun.id).toBe(testRun.id);
            expect(updatedTestRun.workingTime).toBe(hour * 3600 + minutes * 60 + seconds);

            await examTestRun.openTestRunPage(course, exam);
            await examTestRun.getTestRun(testRun.id!).waitFor({ state: 'visible' });
            await expect(examTestRun.getWorkingTime(testRun.id!).filter({ hasText: `${hour}h ${minutes}min ${seconds}s` })).toBeVisible();
            await expect(examTestRun.getStarted(testRun.id!).filter({ hasText: 'No' })).toBeVisible();
            await expect(examTestRun.getSubmitted(testRun.id!).filter({ hasText: 'No' })).toBeVisible();
        });

        test('Conducts a test run', async ({ login, courseManagementAPIRequests, examTestRun, examParticipation, examNavigation }) => {
            await login(instructor);
            const testRun = await courseManagementAPIRequests.createExamTestRun(exam, exerciseArray);

            await examTestRun.startParticipation(instructor, course, exam, testRun.id!);
            await expect(examTestRun.getTestRunRibbon().getByText('Test Run')).toBeVisible();

            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
                await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
            }
            await examParticipation.handInEarly();
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examParticipation.verifyExerciseTitleOnFinalPage(exercise.id!, exercise.exerciseGroup!.title!);
            }
            await examParticipation.checkExamTitle(examTitle);
            await examTestRun.openTestRunPage(course, exam);
            await examTestRun.getTestRun(testRun.id!).waitFor({ state: 'visible' });
            await expect(examTestRun.getStarted(testRun.id!).filter({ hasText: 'Yes' })).toBeVisible();
            await expect(examTestRun.getSubmitted(testRun.id!).filter({ hasText: 'Yes' })).toBeVisible();
        });
    });

    test.describe('Delete a test run', () => {
        let testRun: StudentExam;

        test.beforeEach('Create test run instance', async ({ login, courseManagementAPIRequests }) => {
            await login(instructor);
            testRun = await courseManagementAPIRequests.createExamTestRun(exam, exerciseArray);
        });

        test('Deletes a test run', async ({ login, page, examTestRun }) => {
            await login(instructor);
            await examTestRun.openTestRunPage(course, exam);
            // The test run was created via API in beforeEach, but the page may load
            // before the data is available. Reload until the test run element appears.
            await Commands.reloadUntilFound(page, examTestRun.getTestRun(testRun.id!), 10000, 60000);
            await examTestRun.deleteTestRun(testRun.id!);
            await expect(examTestRun.getTestRun(testRun.id!)).not.toBeVisible();
        });
    });

    test.afterEach('Delete exam', async ({ examAPIRequests }) => {
        await examAPIRequests.deleteExam(exam);
    });
});
