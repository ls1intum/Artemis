import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import { ProgrammingLanguage } from '../../../support/constants';
import { admin, studentOne, studentThree, studentTwo, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { ExerciseMode } from 'app/entities/exercise.model';

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
            await programmingExerciseEditor.startParticipation(course.id!, exercise, studentOne);
            const submission = javaBuildErrorSubmission;
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });
        });

        test('Makes a partially successful submission', async ({ programmingExerciseEditor }) => {
            await programmingExerciseEditor.startParticipation(course.id!, exercise, studentTwo);
            const submission = javaPartiallySuccessfulSubmission;
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });
        });

        test('Makes a successful submission', async ({ programmingExerciseEditor }) => {
            await programmingExerciseEditor.startParticipation(course.id!, exercise, studentThree);
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
                await programmingExerciseEditor.startParticipation(course.id!, exercise, studentOne);
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
            await programmingExerciseEditor.startParticipation(course.id!, exercise, studentOne);
            const submission = pythonAllSuccessful;
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });
        });
    });

    test.describe('Programming exercise team participation', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Create team programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            const teamAssignmentConfig = { minTeamSize: 2, maxTeamSize: 3 };
            exercise = await exerciseAPIRequests.createProgrammingExercise({
                course,
                programmingLanguage: ProgrammingLanguage.JAVA,
                mode: ExerciseMode.TEAM,
                teamAssignmentConfig,
            });
        });

        test.beforeEach('Create an exercise team', async ({ login, userManagementAPIRequests, exerciseAPIRequests }) => {
            await login(admin);
            const studentOneUser = await (await userManagementAPIRequests.getUser(studentOne.username)).json();
            const studentTwoUser = await (await userManagementAPIRequests.getUser(studentTwo.username)).json();
            const studentThreeUser = await (await userManagementAPIRequests.getUser(studentThree.username)).json();
            const tutorUser = await (await userManagementAPIRequests.getUser(tutor.username)).json();
            const students = [studentOneUser, studentTwoUser, studentThreeUser];
            await exerciseAPIRequests.createTeam(exercise.id!, students, tutorUser);
        });

        test('Each team member makes a submission', async ({ programmingExerciseEditor }) => {
            const submission1 = javaBuildErrorSubmission;
            await programmingExerciseEditor.startParticipation(course.id!, exercise, studentOne);
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission1, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(javaBuildErrorSubmission.expectedResult)).toBeVisible();
            });

            const submission2 = { ...javaPartiallySuccessfulSubmission, deleteFiles: [] };
            await programmingExerciseEditor.startParticipation(course.id!, exercise, studentTwo, true);
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission2, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(javaPartiallySuccessfulSubmission.expectedResult)).toBeVisible();
            });

            const submission3 = { ...javaAllSuccessfulSubmission, deleteFiles: [] };
            await programmingExerciseEditor.startParticipation(course.id!, exercise, studentThree, true);
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission3, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(javaAllSuccessfulSubmission.expectedResult)).toBeVisible();
            });
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
