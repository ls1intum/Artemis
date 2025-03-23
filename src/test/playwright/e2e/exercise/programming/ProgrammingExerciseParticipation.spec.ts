import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import { BASE_API, ExerciseCommit, ExerciseMode, ProgrammingLanguage } from '../../../support/constants';
import { test } from '../../../support/fixtures';
import { BrowserContext, Page, expect } from '@playwright/test';
import { SSH_KEYS_PATH, SSH_KEY_NAMES, SshEncryptionAlgorithm, gitClient } from '../../../support/pageobjects/exercises/programming/GitClient';
import * as fs from 'fs/promises';
import path from 'path';
import { SimpleGit } from 'simple-git';
import { Fixtures } from '../../../fixtures/fixtures';
import { createFileWithContent } from '../../../support/utils';
import { ProgrammingExerciseSubmission } from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import { UserCredentials, admin, instructor, studentFour, studentOne, studentTwo, tutor } from '../../../support/users';
import { Team } from 'app/exercise/entities/team.model';
import { GitCloneMethod, ProgrammingExerciseOverviewPage } from '../../../support/pageobjects/exercises/programming/ProgrammingExerciseOverviewPage';
import { Participation } from 'app/exercise/entities/participation/participation.model';

test.describe('Programming exercise participation', { tag: '@sequential' }, () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin, '/');
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
        await courseManagementAPIRequests.addStudentToCourse(course, studentFour);
    });

    const testCases = [
        {
            description: 'Makes a failing Java submission',
            programmingLanguage: ProgrammingLanguage.JAVA,
            submission: javaBuildErrorSubmission,
            commitMessage: 'Initial commit',
        },
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
    ];

    for (const { description, programmingLanguage, submission, commitMessage } of testCases) {
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

                test('Makes a git submission through HTTPS', async ({ page, programmingExerciseOverview }) => {
                    await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                    await makeGitExerciseSubmission(page, programmingExerciseOverview, course, exercise, studentOne, submission, commitMessage);
                });
            });
        }
    }

    test.describe('Programming exercise participation using secure git', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.JAVA });
        });

        test('Makes a git submission through HTTPS using token', async ({ programmingExerciseOverview, page }) => {
            await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
            await makeGitExerciseSubmission(
                page,
                programmingExerciseOverview,
                course,
                exercise,
                studentOne,
                javaAllSuccessfulSubmission,
                'Solution',
                GitCloneMethod.httpsWithToken,
            );
        });

        test.describe('Programming exercise participation using SSH', () => {
            for (const sshAlgorithm of [SshEncryptionAlgorithm.rsa, SshEncryptionAlgorithm.ed25519]) {
                test(`Makes a git submission using SSH with ${sshAlgorithm} key`, async ({ page, programmingExerciseOverview }) => {
                    await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                    await makeGitExerciseSubmission(
                        page,
                        programmingExerciseOverview,
                        course,
                        exercise,
                        studentOne,
                        javaAllSuccessfulSubmission,
                        'Solution',
                        GitCloneMethod.ssh,
                        sshAlgorithm,
                    );
                });
            }

            test.afterEach('Delete SSH key', async ({ accountManagementAPIRequests }) => {
                await accountManagementAPIRequests.deleteSshPublicKey();
            });
        });
    });

    test.describe('Programming exercise team participation', () => {
        let exercise: ProgrammingExercise;
        let participation: Participation;
        let team: Team;
        let tutorUser: any;

        const submissions = [
            { student: studentOne, submission: javaBuildErrorSubmission, commitMessage: 'Initial commit' },
            { student: studentTwo, submission: javaPartiallySuccessfulSubmission, commitMessage: 'Initial implementation' },
        ];

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
            const students = await Promise.all(
                [studentOne, studentTwo].map(async (student) => {
                    const response = await userManagementAPIRequests.getUser(student.username);
                    return response.json();
                }),
            );
            tutorUser = await (await userManagementAPIRequests.getUser(tutor.username)).json();
            const response = await exerciseAPIRequests.createTeam(exercise.id!, students, tutorUser);
            team = await response.json();
        });

        test('Team members make git submissions', async ({ login, page, courseList, courseOverview, programmingExerciseOverview }) => {
            const firstSubmission = submissions[0];
            await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, firstSubmission.student);
            await makeGitExerciseSubmission(
                page,
                programmingExerciseOverview,
                course,
                exercise,
                firstSubmission.student,
                firstSubmission.submission,
                firstSubmission.commitMessage,
            );

            for (let i = 1; i < submissions.length; i++) {
                const { student, submission, commitMessage } = submissions[i];
                await login(student, '/');
                await page.waitForURL(/\/courses/);
                await courseList.openCourse(course.id!);
                await courseOverview.openExercise(exercise.title!);
                submission.deleteFiles = [];
                await makeGitExerciseSubmission(page, programmingExerciseOverview, course, exercise, student, submission, commitMessage);
            }

            await login(studentFour, '/');
            await page.waitForURL(/\/courses/);
            await courseList.openCourse(course.id!);
            await courseOverview.openExercise(exercise.title!);
            await expect(programmingExerciseOverview.getCodeButton()).not.toBeVisible();
        });

        test('Students without a team can not participate in the team exercise', async ({ login, page, courseList, courseOverview, programmingExerciseOverview }) => {
            await login(studentFour, '/');
            await page.waitForURL(/\/courses/);
            await courseList.openCourse(course.id!);
            await courseOverview.openExercise(exercise.title!);
            await expect(programmingExerciseOverview.getExerciseDetails().getByText('No team yet')).toBeVisible();
            await expect(courseOverview.getStartExerciseButton(exercise.id!)).not.toBeVisible();
            await expect(programmingExerciseOverview.getCodeButton()).not.toBeVisible();
        });

        test('Students of other teams have their own submission', async ({
            login,
            userManagementAPIRequests,
            exerciseAPIRequests,
            page,
            courseList,
            courseOverview,
            programmingExerciseOverview,
        }) => {
            await login(admin);
            const response = await userManagementAPIRequests.getUser(studentFour.username);
            const studentFourUser = await response.json();
            await exerciseAPIRequests.createTeam(exercise.id!, [studentFourUser], tutorUser);

            await login(studentFour, '/');
            await page.waitForURL(/\/courses/);
            await courseList.openCourse(course.id!);
            await courseOverview.openExercise(exercise.title!);
            await expect(programmingExerciseOverview.getCodeButton()).not.toBeVisible();
            await expect(programmingExerciseOverview.getExerciseDetails().getByText('Not yet started')).toBeVisible();
            await courseOverview.startExercise(exercise.id!);
            await expect(programmingExerciseOverview.getExerciseDetails().getByText('No graded result')).toBeVisible();
        });

        test.describe('Check team participation', () => {
            test.beforeEach('Each team member makes a submission', async ({ login, waitForExerciseBuildToFinish, exerciseAPIRequests }) => {
                for (const { student, submission } of submissions) {
                    await login(student);
                    const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
                    participation = await response.json();
                    for (const file of submission.files) {
                        const filename = `src/${submission.packageName.replace(/\./g, '/')}/${file.name}`;
                        await exerciseAPIRequests.createProgrammingExerciseFile(participation.id!, filename);
                    }
                    await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, submission);
                    await waitForExerciseBuildToFinish(exercise.id!);
                }
            });

            test('Instructor checks the participation', async ({ login, navigationBar, courseManagement, courseManagementExercises, programmingExerciseParticipations }) => {
                await login(instructor);
                await navigationBar.openCourseManagement();
                await courseManagement.openExercisesOfCourse(course.id!);
                await courseManagementExercises.openExerciseParticipations(exercise.id!);
                await programmingExerciseParticipations.getParticipation(participation.id!).waitFor({ state: 'visible' });
                await programmingExerciseParticipations.checkParticipationTeam(participation.id!, team.name!);
                await programmingExerciseParticipations.checkParticipationBuildPlan(participation);
                const studentUsernames = submissions.map(({ student }) => student.username!);
                await programmingExerciseParticipations.checkParticipationStudents(participation.id!, studentUsernames);
                const programmingExerciseRepository = await programmingExerciseParticipations.openRepositoryOnNewPage(participation.id!);
                await programmingExerciseRepository.openCommitHistory();
                const commitMessage = 'Changes by Online Editor';
                const commits: ExerciseCommit[] = submissions.map(({ submission }) => ({ message: commitMessage, result: submission.expectedResult }));
                await programmingExerciseRepository.checkCommitHistory(commits);
            });
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});

async function makeGitExerciseSubmission(
    page: Page,
    programmingExerciseOverview: ProgrammingExerciseOverviewPage,
    course: Course,
    exercise: ProgrammingExercise,
    student: UserCredentials,
    submission: any,
    commitMessage: string,
    cloneMethod: GitCloneMethod = GitCloneMethod.https,
    sshAlgorithm: SshEncryptionAlgorithm = SshEncryptionAlgorithm.ed25519,
) {
    await programmingExerciseOverview.openCloneMenu(cloneMethod);
    if (cloneMethod == GitCloneMethod.ssh) {
        await expect(programmingExerciseOverview.getCloneUrlButton()).toBeDisabled();
        const sshKeyNotFoundAlert = page.locator('.alert', { hasText: 'To use ssh, you need to add an ssh key to your account' });
        await expect(sshKeyNotFoundAlert).toBeVisible();
        await setupSSHCredentials(page.context(), sshAlgorithm);
        await page.reload();
        await programmingExerciseOverview.openCloneMenu(cloneMethod);
    }
    let repoUrl = await programmingExerciseOverview.copyCloneUrl();
    if (cloneMethod == GitCloneMethod.https) {
        repoUrl = repoUrl.replace(student.username!, `${student.username!}:${student.password!}`);
    }
    console.log(`Cloning repository from ${repoUrl}`);
    const urlParts = repoUrl.split('/');
    const repoName = urlParts[urlParts.length - 1];
    let exerciseRepo;
    if (cloneMethod == GitCloneMethod.ssh) {
        exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName, SSH_KEY_NAMES[sshAlgorithm]);
    } else {
        exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);
    }
    console.log(`Cloned repository successfully. Pushing files...`);
    await pushGitSubmissionFiles(exerciseRepo, repoName, student, submission, commitMessage);
    await fs.rmdir(`./test-exercise-repos/${repoName}`, { recursive: true });
    await page.goto(`courses/${course.id}/exercises/${exercise.id!}`);
    const resultScore = await programmingExerciseOverview.getResultScore();
    await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
}

async function setupSSHCredentials(context: BrowserContext, sshAlgorithm: SshEncryptionAlgorithm) {
    console.log(`Setting up SSH credentials with key ${SSH_KEY_NAMES[sshAlgorithm]}`);
    const page = await context.newPage();
    const sshKeyPath = path.join(SSH_KEYS_PATH, `${SSH_KEY_NAMES[sshAlgorithm]}.pub`);
    const sshKey = await fs.readFile(sshKeyPath, 'utf8');
    await page.goto('user-settings/ssh');
    await page.getByTestId('addNewSshKeyButton').click();
    await page.getByTestId('sshKeyField').fill(sshKey!);
    const responsePromise = page.waitForResponse(`${BASE_API}/programming/ssh-settings/public-key`);
    await page.getByTestId('saveSshKeyButton').click();
    await responsePromise;
    await page.close();
}

/**
 * Helper function to make a submission to a git repository.
 * @param exerciseRepo - The git repository to which the submission should be made.
 * @param exerciseRepoName - The name of the git repository.
 * @param user - The user who is making the submission.
 * @param submission - The programming exercise submission to be made.
 * @param commitMessage - The commit message for the submission.
 * @param deleteFiles - Whether to delete files from the repository directory before making the submission.
 */
async function pushGitSubmissionFiles(
    exerciseRepo: SimpleGit,
    exerciseRepoName: string,
    user: UserCredentials,
    submission: ProgrammingExerciseSubmission,
    commitMessage: string,
    deleteFiles: boolean = true,
) {
    let sourcePath = '';
    if (submission.packageName) {
        const packagePath = submission.packageName.replace(/\./g, '/');
        sourcePath = `src/${packagePath}/`;
    }

    if (deleteFiles) {
        for (const fileName of submission.deleteFiles) {
            const filePath = `./${sourcePath}${fileName}`;
            await exerciseRepo.rm(filePath);
        }
    }

    for (const file of submission.files) {
        const filePath = `./${sourcePath}${file.name}`;
        const sourceCode = await Fixtures.get(file.path);
        await createFileWithContent(`./test-exercise-repos/${exerciseRepoName}/${filePath}`, sourceCode!);
        await exerciseRepo.add(`./${filePath}`);
    }

    await exerciseRepo.addConfig('user.email', `${user.username}@example.com`);
    await exerciseRepo.addConfig('user.name', user.username);
    await exerciseRepo.commit(commitMessage);
    await exerciseRepo.push();
}
