import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import cBuildErrorSubmission from '../../../fixtures/exercise/programming/c/build_error/submission.json';
import cPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/c/partially_successful/submission.json';
import { ExerciseCommit, ExerciseMode, ProgrammingLanguage } from '../../../support/constants';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { BUILD_RESULT_TIMEOUT } from '../../../support/timeouts';
import { SshEncryptionAlgorithm } from '../../../support/pageobjects/exercises/programming/GitClient';
import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import { admin, instructor, studentFour, studentOne, studentTwo, tutor } from '../../../support/users';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { GitCloneMethod, ProgrammingExerciseOverviewPage } from 'src/test/playwright/support/pageobjects/exercises/programming/ProgrammingExerciseOverviewPage';

import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { GitExerciseParticipation } from '../../../support/pageobjects/exercises/programming/GitExerciseParticipation';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.programmingParticipation.id } as any;

// Basic submission tests: use seed course, only create exercises per test.
test.describe('Programming exercise basic submissions', { tag: '@slow' }, () => {
    const testCases = [
        {
            description: 'Makes a failing C submission',
            programmingLanguage: ProgrammingLanguage.C,
            submission: cBuildErrorSubmission,
            commitMessage: 'Initial commit',
        },
        { description: 'Makes a successful C submission', programmingLanguage: ProgrammingLanguage.C, submission: cAllSuccessful, commitMessage: 'Implemented all tasks' },
    ];
    for (const testCase of testCases) {
        const { description, programmingLanguage, submission, commitMessage } = testCase;
        test.describe(description, () => {
            let exercise: ProgrammingExercise;

            test.beforeEach('Setup programming exercise', async ({ login, exerciseAPIRequests }) => {
                await login(admin);
                exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage });
            });

            test('Makes a submission using code editor', async ({ programmingExerciseOverview, programmingExerciseEditor }) => {
                // C builds can take longer under CI parallel load
                test.slow();
                await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                await programmingExerciseOverview.openCodeEditor(exercise.id!);
                await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                    const resultScore = programmingExerciseEditor.getResultScoreFromExercise(exercise.id!);
                    await expect(resultScore).toContainText(submission.expectedResult, { timeout: BUILD_RESULT_TIMEOUT * 2 });
                });
            });

            test('Makes a git submission through HTTPS', async ({ programmingExerciseOverview, waitForParticipationBuildToFinish }) => {
                test.slow();
                const participationId = await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentOne, submission, commitMessage);
                const participation = await waitForParticipationBuildToFinish(participationId);
                ProgrammingExerciseOverviewPage.verifyResultScore(participation, submission.expectedResult);
            });
        });
    }
});

test.describe('Programming exercise advanced participation', { tag: '@slow' }, () => {
    test.describe('Programming exercise participation using secure git', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
        });

        test('Makes a git submission through HTTPS using token', async ({ programmingExerciseOverview, waitForParticipationBuildToFinish }) => {
            test.slow();
            const participationId = await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
            await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentOne, cAllSuccessful, 'Solution', GitCloneMethod.httpsWithToken);
            const participation = await waitForParticipationBuildToFinish(participationId);
            ProgrammingExerciseOverviewPage.verifyResultScore(participation, cAllSuccessful.expectedResult);
        });

        test.describe.serial('Programming exercise participation using SSH', () => {
            test.beforeEach('Ensure no SSH key exists', async ({ login, accountManagementAPIRequests }) => {
                await login(studentOne);
                await accountManagementAPIRequests.deleteSshPublicKey();
            });

            for (const sshAlgorithm of [SshEncryptionAlgorithm.rsa, SshEncryptionAlgorithm.ed25519]) {
                test(`Makes a git submission using SSH with ${sshAlgorithm} key`, async ({ page, programmingExerciseOverview, waitForParticipationBuildToFinish }) => {
                    test.slow();
                    const participationId = await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                    await programmingExerciseOverview.openCloneMenu(GitCloneMethod.ssh);
                    await expect(programmingExerciseOverview.getCloneUrlButton()).toBeDisabled();
                    const sshKeyNotFoundAlert = page.locator('.alert', { hasText: 'To use ssh, you need to add an ssh key to your account' });
                    await expect(sshKeyNotFoundAlert).toBeVisible();
                    await GitExerciseParticipation.setupSSHCredentials(page.context(), sshAlgorithm);
                    await page.reload();
                    await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentOne, cAllSuccessful, 'Solution', GitCloneMethod.ssh, sshAlgorithm);
                    const participation = await waitForParticipationBuildToFinish(participationId);
                    ProgrammingExerciseOverviewPage.verifyResultScore(participation, cAllSuccessful.expectedResult);
                });
            }

            test.afterEach('Delete SSH key', async ({ login, accountManagementAPIRequests }) => {
                await login(studentOne);
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
            { student: studentOne, submission: cBuildErrorSubmission, commitMessage: 'Initial commit' },
            { student: studentTwo, submission: cPartiallySuccessfulSubmission, commitMessage: 'Initial implementation' },
        ];

        test.beforeEach('Create team programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            const teamAssignmentConfig = { minTeamSize: 2, maxTeamSize: 3 };
            exercise = await exerciseAPIRequests.createProgrammingExercise({
                course,
                programmingLanguage: ProgrammingLanguage.C,
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
            expect(response.ok(), `Team creation failed: ${response.status()} ${response.statusText()}`).toBeTruthy();
            team = await response.json();
        });

        test('Team members make git submissions', async ({ login, page, courseList, courseOverview, programmingExerciseOverview, waitForParticipationBuildToFinish }) => {
            test.slow();
            const firstSubmission = submissions[0];
            const firstParticipationId = await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, firstSubmission.student);
            await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, firstSubmission.student, firstSubmission.submission, firstSubmission.commitMessage);
            const firstParticipation = await waitForParticipationBuildToFinish(firstParticipationId);
            ProgrammingExerciseOverviewPage.verifyResultScore(firstParticipation, firstSubmission.submission.expectedResult);

            for (let i = 1; i < submissions.length; i++) {
                const { student, submission, commitMessage } = submissions[i];
                await login(student, '/');
                await page.waitForURL(/\/courses/);
                await courseList.openCourse(course.id!);
                await courseOverview.openExercise(exercise.title!);
                submission.deleteFiles = [];
                await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, student, submission, commitMessage);
                await programmingExerciseOverview.checkResultScoreAfterBuild(course.id!, exercise.id!, submission.expectedResult);
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
            test.beforeEach('Each team member makes a submission', async ({ login, waitForParticipationBuildToFinish, exerciseAPIRequests }) => {
                const createdFiles = new Set<string>();
                for (const { student, submission } of submissions) {
                    await login(student);
                    const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
                    participation = await response.json();
                    for (const file of submission.files) {
                        const packageName = (submission as any).packageName;
                        const filename = packageName ? `src/${packageName.replace(/\./g, '/')}/${file.name}` : file.name;
                        if (!createdFiles.has(filename)) {
                            await exerciseAPIRequests.createProgrammingExerciseFile(participation.id!, filename);
                            createdFiles.add(filename);
                        }
                    }
                    await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, submission);
                    await waitForParticipationBuildToFinish(participation.id!);
                }
            });

            test('Instructor checks the participation', async ({ login, navigationBar, courseManagement, courseManagementExercises, programmingExerciseParticipations }) => {
                await login(instructor, '/');
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

    test.describe('Instructor Makes a git submission to the student participation', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
        });

        test('Makes a git submission through HTTPS', async ({
            login,
            page,
            programmingExerciseParticipations,
            programmingExerciseOverview,
            programmingExerciseSubmissions,
            waitForExerciseBuildToFinish,
        }) => {
            await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
            await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentOne, cAllSuccessful, 'student commit');
            await login(instructor);
            await waitForExerciseBuildToFinish(exercise.id!);
            await page.goto(`/course-management/${course.id}/programming-exercises/${exercise.id!}/participations`);
            await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, instructor, cPartiallySuccessfulSubmission, 'instructor commit');
            await page.goto(`/course-management/${course.id}/programming-exercises/${exercise.id!}/participations`);
            await programmingExerciseParticipations.openStudentParticipationSubmissions(studentOne);
            await programmingExerciseSubmissions.checkInstructorSubmission(60000);
            await programmingExerciseSubmissions.checkStudentSubmission();
        });
    });
});
