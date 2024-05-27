import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import swiftAllSuccessfulSubmission from '../../../fixtures/exercise/programming/swift/all_successful/submission.json';
import haskellAllSuccessfulSubmission from '../../../fixtures/exercise/programming/haskell/all_successful/submission.json';
import kotlinAllSuccessfulSubmission from '../../../fixtures/exercise/programming/kotlin/all_successful/submission.json';
import vhdlAllSuccessfulSubmission from '../../../fixtures/exercise/programming/vhdl/all_successful/submission.json';
import assemblerAllSuccessfulSubmission from '../../../fixtures/exercise/programming/assembler/all_successful/submission.json';
import ocamlAllSuccessfulSubmission from '../../../fixtures/exercise/programming/ocaml/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import { ProgrammingLanguage } from '../../../support/constants';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { gitClient } from '../../../support/pageobjects/exercises/programming/GitClient';
import * as fs from 'fs/promises';
import { SimpleGit } from 'simple-git';
import { Fixtures } from '../../../fixtures/fixtures';
import { createFileWithContent } from '../../../support/utils';
import { ProgrammingExerciseSubmission } from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import { UserCredentials, admin, studentOne } from '../../../support/users';

test.describe('Programming exercise participation', () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin, '/');
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
    });

    const testCases = [
        { description: 'Makes a failing Java submission', programmingLanguage: ProgrammingLanguage.JAVA, submission: javaBuildErrorSubmission, commitMessage: 'Initial commit' },
        {
            description: 'Makes a partially successful Java submission',
            programmingLanguage: ProgrammingLanguage.JAVA,
            submission: javaPartiallySuccessfulSubmission,
            commitMessage: 'Initial implementation',
        },
        {
            description: 'Makes a successful Java submission',
            programmingLanguage: ProgrammingLanguage.JAVA,
            submission: javaAllSuccessfulSubmission,
            commitMessage: 'Implemented all tasks',
        },
        { description: 'Makes a successful C submission', programmingLanguage: ProgrammingLanguage.C, submission: cAllSuccessful, commitMessage: 'Implemented all tasks' },
        {
            description: 'Makes a successful Python submission',
            programmingLanguage: ProgrammingLanguage.PYTHON,
            submission: pythonAllSuccessful,
            commitMessage: 'Implemented all tasks',
        },
        {
            description: 'Makes a successful Swift submission',
            programmingLanguage: ProgrammingLanguage.SWIFT,
            submission: swiftAllSuccessfulSubmission,
            commitMessage: 'Implemented all tasks',
        },
        {
            description: 'Makes a successful Haskell submission',
            programmingLanguage: ProgrammingLanguage.HASKELL,
            submission: haskellAllSuccessfulSubmission,
            commitMessage: 'Implemented all tasks',
        },
        {
            description: 'Makes a successful Kotlin submission',
            programmingLanguage: ProgrammingLanguage.KOTLIN,
            submission: kotlinAllSuccessfulSubmission,
            commitMessage: 'Implemented all tasks',
        },
        {
            description: 'Makes a successful VHDL submission',
            programmingLanguage: ProgrammingLanguage.VHDL,
            submission: vhdlAllSuccessfulSubmission,
            commitMessage: 'Implemented all tasks',
        },
        {
            description: 'Makes a successful Assembler submission',
            programmingLanguage: ProgrammingLanguage.ASSEMBLER,
            submission: assemblerAllSuccessfulSubmission,
            commitMessage: 'Implemented all tasks',
        },
        {
            description: 'Makes a successful Ocaml submission',
            programmingLanguage: ProgrammingLanguage.OCAML,
            submission: ocamlAllSuccessfulSubmission,
            commitMessage: 'Implemented all tasks',
        },
    ];

    for (const { description, programmingLanguage, submission } of testCases) {
        // Skip C tests within Jenkins used by the Postgres setup, since C is currently not supported there
        // See https://github.com/ls1intum/Artemis/issues/6994
        if (programmingLanguage !== ProgrammingLanguage.C || process.env.PLAYWRIGHT_DB_TYPE !== 'Postgres') {
            test.describe(description, () => {
                let exercise: ProgrammingExercise;

                test.beforeEach('Setup programming exercise', async ({ login, exerciseAPIRequests }) => {
                    await login(admin);
                    exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage });
                });

                test('Makes a submission using code editor', async ({ programmingExerciseOverview, programmingExerciseEditor }) => {
                    await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                    await programmingExerciseOverview.openCodeEditor(exercise.id!);
                    await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                        const resultScore = await programmingExerciseEditor.getResultScore();
                        await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
                    });
                });

                test('Makes a submission using git', async ({ page, programmingExerciseOverview }) => {
                    await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                    let repoUrl = await programmingExerciseOverview.getRepoUrl();
                    if (process.env.CI === 'true') {
                        repoUrl = repoUrl.replace('localhost', 'artemis-app');
                    }
                    repoUrl = repoUrl.replace(studentOne.username!, `${studentOne.username!}:${studentOne.password!}`);
                    const urlParts = repoUrl.split('/');
                    const repoName = urlParts[urlParts.length - 1];
                    const exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);
                    const commitMessage = 'Implemented all tasks';
                    await makeGitSubmission(exerciseRepo, repoName, studentOne, submission, commitMessage);
                    await fs.rmdir(`./test-exercise-repos/${repoName}`, { recursive: true });
                    await page.goto(`courses/${course.id}/exercises/${exercise.id!}`);
                    const resultScore = await programmingExerciseOverview.getResultScore();
                    await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
                });
            });
        }
    }

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});

/**
 * Helper function to make a submission to a git repository.
 * @param exerciseRepo - The git repository to which the submission should be made.
 * @param exerciseRepoName - The name of the git repository.
 * @param user - The user who is making the submission.
 * @param submission - The programming exercise submission to be made.
 * @param commitMessage - The commit message for the submission.
 * @param deleteFiles - Whether to delete files from the repository directory before making the submission.
 */
async function makeGitSubmission(
    exerciseRepo: SimpleGit,
    exerciseRepoName: string,
    user: UserCredentials,
    submission: ProgrammingExerciseSubmission,
    commitMessage: string,
    deleteFiles: boolean = true,
) {
    const sourceDirectory = submission.sourceDirectory ?? '';

    if (deleteFiles) {
        for (const fileName of submission.deleteFiles) {
            const filePath = `./${sourceDirectory}${fileName}`;
            await exerciseRepo.rm(filePath);
        }
    }

    for (const file of submission.files) {
        const filePath = `./${file.directory ?? sourceDirectory}${file.name}`;
        const sourceCode = await Fixtures.get(file.fixturePath);
        await createFileWithContent(`./test-exercise-repos/${exerciseRepoName}/${filePath}`, sourceCode!);
        await exerciseRepo.add(`./${filePath}`);
    }

    await exerciseRepo.addConfig('user.email', `${user.username}@example.com`);
    await exerciseRepo.addConfig('user.name', user.username);
    await exerciseRepo.commit(commitMessage);
    await exerciseRepo.push();
}
