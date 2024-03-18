import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import { ProgrammingLanguage } from '../../../support/constants';
import { admin, studentOne, studentThree, studentTwo } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

test.describe('Programming exercise participation', () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin, '/');
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
        courseManagementAPIRequests.addStudentToCourse(course, studentThree);
    });

    test.describe('Java programming exercise', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup java programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.JAVA });
        });

        test('Makes a failing submission', async ({ programmingExerciseEditor }) => {
            await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
            const submission = javaBuildErrorSubmission;
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });
        });

        test('Makes a partially successful submission', async ({ programmingExerciseEditor }) => {
            await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentTwo);
            const submission = javaPartiallySuccessfulSubmission;
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });
        });

        test('Makes a successful submission', async ({ programmingExerciseEditor }) => {
            await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentThree);
            const submission = javaAllSuccessfulSubmission;
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });
        });
    });

    // Skip C tests within Jenkins used by the Postgres setup, since C is currently not supported there
    // See https://github.com/ls1intum/Artemis/issues/6994
    if (process.env.PLAYWRIGHT_DB_TYPE !== 'Postgres') {
        test.describe('C programming exercise', () => {
            let exercise: ProgrammingExercise;

            test.beforeEach('Setup c programming exercise', async ({ login, exerciseAPIRequests }) => {
                await login(admin);
                exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
            });

            test('Makes a submission', async ({ programmingExerciseEditor }) => {
                await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
                const submission = cAllSuccessful;
                await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                    const resultScore = await programmingExerciseEditor.getResultScore();
                    await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
                });
            });
        });
    }

    test.describe('Python programming exercise', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup python programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.PYTHON });
        });

        test('Makes a submission', async ({ programmingExerciseEditor }) => {
            await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
            const submission = pythonAllSuccessful;
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
