import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import { ExerciseCommit, ProgrammingLanguage } from '../../../support/constants';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { gitClient } from '../../../support/pageobjects/exercises/programming/GitClient';
import * as fs from 'fs/promises';
import { SimpleGit } from 'simple-git';
import { Fixtures } from '../../../fixtures/fixtures';
import { createFileWithContent } from '../../../support/utils';
import { ProgrammingExerciseSubmission } from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import { admin, studentOne, studentThree, studentTwo } from '../../../support/users';
import { RepositoryPage } from '../../../support/pageobjects/exercises/programming/RepositoryPage';

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

        test.describe('Make a submission using code editor', () => {
            test('Makes a failing submission', async ({ programmingExerciseOverview, programmingExerciseEditor }) => {
                await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                await programmingExerciseOverview.openCodeEditor(exercise.id!);
                const submission = javaBuildErrorSubmission;
                await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                    const resultScore = await programmingExerciseEditor.getResultScore();
                    await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
                });
            });

            test('Makes a partially successful submission', async ({ programmingExerciseOverview, programmingExerciseEditor }) => {
                await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentTwo);
                await programmingExerciseOverview.openCodeEditor(exercise.id!);
                const submission = javaPartiallySuccessfulSubmission;
                await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                    const resultScore = await programmingExerciseEditor.getResultScore();
                    await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
                });
            });

            test('Makes a successful submission', async ({ programmingExerciseOverview, programmingExerciseEditor }) => {
                await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentThree);
                await programmingExerciseOverview.openCodeEditor(exercise.id!);
                const submission = javaAllSuccessfulSubmission;
                await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                    const resultScore = await programmingExerciseEditor.getResultScore();
                    await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
                });
            });
        });

        test.describe('Make a submission using git', () => {
            test('Makes a failing submission', async ({ page, programmingExerciseOverview, programmingExerciseEditor }) => {
                await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                const repoUrl = await programmingExerciseOverview.getRepoUrl();
                const urlParts = repoUrl.split('/');
                const repoName = urlParts[urlParts.length - 1];
                const exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);
                const submission = javaBuildErrorSubmission;
                const commitMessage = "Let's try and see";
                await makeGitSubmission(exerciseRepo, repoName, submission, commitMessage);
                await fs.rmdir(`./test-exercise-repos/${repoName}`, { recursive: true });
                await page.goto(`courses/${course.id}/exercises/${exercise.id!}`);
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });

            test('Makes a partially successful submission', async ({ page, programmingExerciseOverview, programmingExerciseEditor }) => {
                await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                const repoUrl = await programmingExerciseOverview.getRepoUrl();
                const urlParts = repoUrl.split('/');
                const repoName = urlParts[urlParts.length - 1];
                const exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);
                const submission = javaPartiallySuccessfulSubmission;
                const commitMessage = 'Initial implementation';
                await makeGitSubmission(exerciseRepo, repoName, submission, commitMessage);
                await fs.rmdir(`./test-exercise-repos/${repoName}`, { recursive: true });
                await page.goto(`courses/${course.id}/exercises/${exercise.id!}`);
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });

            test('Makes a successful submission', async ({ page, programmingExerciseOverview, programmingExerciseEditor }) => {
                await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                const repoUrl = await programmingExerciseOverview.getRepoUrl();
                const urlParts = repoUrl.split('/');
                const repoName = urlParts[urlParts.length - 1];
                const exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);
                const submission = javaAllSuccessfulSubmission;
                const commitMessage = 'Implemented all tasks';
                await makeGitSubmission(exerciseRepo, repoName, submission, commitMessage);
                await fs.rmdir(`./test-exercise-repos/${repoName}`, { recursive: true });
                await page.goto(`courses/${course.id}/exercises/${exercise.id!}`);
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
            });

            test('Checks commit history', async ({ page, programmingExerciseOverview }) => {
                await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                const repoUrl = await programmingExerciseOverview.getRepoUrl();
                const urlParts = repoUrl.split('/');
                const repoName = urlParts[urlParts.length - 1];
                const exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);

                const partialSubmission = javaPartiallySuccessfulSubmission;
                const initialCommitMessage = 'Initial commit';
                await makeGitSubmission(exerciseRepo, repoName, partialSubmission, initialCommitMessage);

                const correctSubmission = javaAllSuccessfulSubmission;
                const finalCommitMessage = 'Implemented all tasks';
                await makeGitSubmission(exerciseRepo, repoName, correctSubmission, finalCommitMessage, false);
                await fs.rmdir(`./test-exercise-repos/${repoName}`, { recursive: true });
                await page.goto(`courses/${course.id}/exercises/${exercise.id!}`);
                await programmingExerciseOverview.getResultScore();

                // Use repository page opened in new tab
                const repositoryPage = await programmingExerciseOverview.openRepository();
                const programmingExerciseRepository = new RepositoryPage(repositoryPage);
                await programmingExerciseRepository.openCommitHistory();

                // All commits in descending order by their date
                const commits: ExerciseCommit[] = [
                    { message: finalCommitMessage, result: correctSubmission.expectedResult },
                    { message: initialCommitMessage, result: partialSubmission.expectedResult },
                    { message: 'Exercise-Template pushed by Artemis' },
                ];
                await programmingExerciseRepository.checkCommitHistory(commits);
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

            test('Makes a submission', async ({ programmingExerciseOverview, programmingExerciseEditor }) => {
                await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                await programmingExerciseOverview.openCodeEditor(exercise.id!);
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

        test('Makes a submission', async ({ programmingExerciseOverview, programmingExerciseEditor }) => {
            await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
            await programmingExerciseOverview.openCodeEditor(exercise.id!);
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

async function makeGitSubmission(exerciseRepo: SimpleGit, exerciseRepoName: string, submission: ProgrammingExerciseSubmission, commitMessage: string, deleteFiles: boolean = true) {
    if (deleteFiles) {
        for (const fileName of submission.deleteFiles) {
            const packagePath = submission.packageName!.replace(/\./g, '/');
            const filePath = `./src/${packagePath}/${fileName}`;
            await exerciseRepo.rm(filePath);
        }
    }

    for (const file of submission.files) {
        const packagePath = submission.packageName!.replace(/\./g, '/');
        const filePath = `src/${packagePath}/${file.name}`;
        const sourceCode = await Fixtures.get(file.path);
        await createFileWithContent(`./test-exercise-repos/${exerciseRepoName}/${filePath}`, sourceCode!);
        await exerciseRepo.add(`./${filePath}`);
    }
    await exerciseRepo.commit(commitMessage);
    await exerciseRepo.push();
}
