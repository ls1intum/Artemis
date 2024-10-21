import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import { ExerciseCommit, ExerciseMode, ProgrammingLanguage } from '../../../support/constants';
import { test } from '../../../support/fixtures';
import { Page, expect } from '@playwright/test';
import { gitClient } from '../../../support/pageobjects/exercises/programming/GitClient';
import * as fs from 'fs/promises';
import { SimpleGit } from 'simple-git';
import { Fixtures } from '../../../fixtures/fixtures';
import { createFileWithContent } from '../../../support/utils';
import { ProgrammingExerciseSubmission } from '../../../support/pageobjects/exercises/programming/OnlineEditorPage';
import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import { UserCredentials, admin, instructor, studentFour, studentOne, studentTwo, tutor } from '../../../support/users';
import { Team } from 'app/entities/team.model';
import { ProgrammingExerciseOverviewPage } from '../../../support/pageobjects/exercises/programming/ProgrammingExerciseOverviewPage';
import { Participation } from 'app/entities/participation/participation.model';

test.describe('Programming exercise participation', () => {
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

                test('Makes a submission using git', async ({ page, programmingExerciseOverview }) => {
                    await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                    await makeGitExerciseSubmission(page, programmingExerciseOverview, course, exercise, studentOne, submission, commitMessage);
                });
            });
        }
    }

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
) {
    let repoUrl = await programmingExerciseOverview.getRepoUrl();
    // if (process.env.CI === 'true') {
    //     repoUrl = repoUrl.replace('localhost', 'artemis-app');
    // }
    repoUrl = repoUrl.replace(student.username!, `${student.username!}:${student.password!}`);
    repoUrl = repoUrl.replace(`:**********`, ``);
    const urlParts = repoUrl.split('/');
    const repoName = urlParts[urlParts.length - 1];
    const exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);
    await pushGitSubmissionFiles(exerciseRepo, repoName, student, submission, commitMessage);
    await fs.rmdir(`./test-exercise-repos/${repoName}`, { recursive: true });
    await page.goto(`courses/${course.id}/exercises/${exercise.id!}`);
    const resultScore = await programmingExerciseOverview.getResultScore();
    await expect(resultScore.getByText(submission.expectedResult)).toBeVisible();
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
