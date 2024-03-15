import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import { ProgrammingLanguage } from '../../../support/constants';
import { admin, studentFour, studentOne, studentThree, studentTwo } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { gitClient } from '../../../support/pageobjects/exercises/programming/GitClient';
import * as fs from 'fs/promises';

test.describe('Programming exercise participation', () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin, '/');
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
        await courseManagementAPIRequests.addStudentToCourse(course, studentThree);
        await courseManagementAPIRequests.addStudentToCourse(course, studentFour);
    });

    test.describe('Java programming exercise', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup java programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.JAVA });
        });

        test.describe('Make a submission using code editor', () => {
            test('Makes a failing submission', async ({ programmingExerciseEditor }) => {
                await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
                await programmingExerciseEditor.openCodeEditor(exercise.id!);
                const submission = javaBuildErrorSubmission;
                await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                    const resultScore = await programmingExerciseEditor.getResultScore();
                    await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
                });
            });

            test('Makes a partially successful submission', async ({ programmingExerciseEditor }) => {
                await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentTwo);
                await programmingExerciseEditor.openCodeEditor(exercise.id!);
                const submission = javaPartiallySuccessfulSubmission;
                await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                    const resultScore = await programmingExerciseEditor.getResultScore();
                    await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
                });
            });

            test('Makes a successful submission', async ({ programmingExerciseEditor }) => {
                await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentThree);
                await programmingExerciseEditor.openCodeEditor(exercise.id!);
                const submission = javaAllSuccessfulSubmission;
                await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                    const resultScore = await programmingExerciseEditor.getResultScore();
                    await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
                });
            });
        });

        test.describe('Make a submission using git', () => {
            const exerciseRepos: string[] = [];

            test('Makes a failing submission', async ({ page, programmingExerciseEditor }) => {
                await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
                const repoUrl = await programmingExerciseEditor.getRepoUrl();
                const urlParts = repoUrl.split('/');
                const repoName = urlParts[urlParts.length - 1];
                exerciseRepos.push(repoName);
                const exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);
                const submission = javaBuildErrorSubmission;
                await programmingExerciseEditor.makeGitSubmissionAndVerifyResults(exerciseRepo, repoName, submission);
                await page.goto(`courses/${course.id}/exercises/${exercise.id!}`);
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });

            test('Makes a partially successful submission', async ({ page, programmingExerciseEditor }) => {
                await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
                const repoUrl = await programmingExerciseEditor.getRepoUrl();
                const urlParts = repoUrl.split('/');
                const repoName = urlParts[urlParts.length - 1];
                exerciseRepos.push(repoName);
                const exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);
                const submission = javaPartiallySuccessfulSubmission;
                await programmingExerciseEditor.makeGitSubmissionAndVerifyResults(exerciseRepo, repoName, submission);
                await page.goto(`courses/${course.id}/exercises/${exercise.id!}`);
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });

            test('Makes a successful submission', async ({ page, programmingExerciseEditor }) => {
                await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
                const repoUrl = await programmingExerciseEditor.getRepoUrl();
                const urlParts = repoUrl.split('/');
                const repoName = urlParts[urlParts.length - 1];
                exerciseRepos.push(repoName);
                const exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);
                const submission = javaAllSuccessfulSubmission;
                await programmingExerciseEditor.makeGitSubmissionAndVerifyResults(exerciseRepo, repoName, submission);
                await page.goto(`courses/${course.id}/exercises/${exercise.id!}`);
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });

            test.afterAll('Clean exercise repos', async () => {
                for (const repo of exerciseRepos) {
                    await fs.rmdir(`./test-exercise-repos/${repo}`, { recursive: true });
                }
            });
        });
    });

    // TODO: Add DB_TYPE as Playwright environment variable for checking here
    // Skip C tests within Jenkins used by the Postgres setup, since C is currently not supported there
    // See https://github.com/ls1intum/Artemis/issues/6994
    // if (Cypress.env('DB_TYPE') !== 'Postgres') {
    //     test.describe('C programming exercise', () => {
    //         let exercise: ProgrammingExercise;
    //
    //         test.beforeEach('Setup c programming exercise', async ({ login, exerciseAPIRequests }) => {
    //             await login(admin);
    //             exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
    //         });
    //
    //         test('Makes a submission', async ({ programmingExerciseEditor }) => {
    //             await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
    //             await programmingExerciseEditor.openCodeEditor(exercise.id!);
    //             const submission = cAllSuccessful;
    //             await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
    //                 const resultScore = await programmingExerciseEditor.getResultScore();
    //                 await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
    //             });
    //         });
    //     });
    // }

    test.describe('Python programming exercise', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup python programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.PYTHON });
        });

        test('Makes a submission', async ({ programmingExerciseEditor }) => {
            await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
            await programmingExerciseEditor.openCodeEditor(exercise.id!);
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
