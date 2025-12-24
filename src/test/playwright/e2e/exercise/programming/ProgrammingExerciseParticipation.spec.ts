import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';
import pythonAllSuccessful from '../../../fixtures/exercise/programming/python/all_successful/submission.json';
import { ExerciseCommit, ExerciseMode, ProgrammingLanguage } from '../../../support/constants';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SshEncryptionAlgorithm } from '../../../support/pageobjects/exercises/programming/GitClient';
import cAllSuccessful from '../../../fixtures/exercise/programming/c/all_successful/submission.json';
import { admin, instructor, studentFour, studentOne, studentTwo, tutor } from '../../../support/users';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { GitCloneMethod } from '../../../support/pageobjects/exercises/programming/ProgrammingExerciseOverviewPage';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { GitExerciseParticipation } from '../../../support/pageobjects/exercises/programming/GitExerciseParticipation';

test.describe('Programming exercise participation', { tag: '@sequential' }, () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin, '/');
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
        await courseManagementAPIRequests.addStudentToCourse(course, studentFour);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
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

                test('Makes a git submission through HTTPS', async ({ programmingExerciseOverview }) => {
                    await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                    await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentOne, submission, commitMessage);
                    await programmingExerciseOverview.checkResultScore(submission.expectedResult);
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

        test('Makes a git submission through HTTPS using token', async ({ programmingExerciseOverview }) => {
            await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
            await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentOne, javaAllSuccessfulSubmission, 'Solution', GitCloneMethod.httpsWithToken);
            await programmingExerciseOverview.checkResultScore(javaAllSuccessfulSubmission.expectedResult);
        });

        test.describe('Programming exercise participation using SSH', () => {
            // Clean up SSH keys before each test to ensure clean state
            // This is defensive - the afterEach should clean up, but if it fails or
            // there's server-side caching, this ensures we start with no SSH key
            test.beforeEach('Ensure no SSH key exists', async ({ login, accountManagementAPIRequests }) => {
                await login(studentOne);
                await accountManagementAPIRequests.deleteSshPublicKey();
            });

            for (const sshAlgorithm of [SshEncryptionAlgorithm.rsa, SshEncryptionAlgorithm.ed25519]) {
                test(`Makes a git submission using SSH with ${sshAlgorithm} key`, async ({ page, programmingExerciseOverview }) => {
                    await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                    await programmingExerciseOverview.openCloneMenu(GitCloneMethod.ssh);
                    await expect(programmingExerciseOverview.getCloneUrlButton()).toBeDisabled();
                    const sshKeyNotFoundAlert = page.locator('.alert', { hasText: 'To use ssh, you need to add an ssh key to your account' });
                    await expect(sshKeyNotFoundAlert).toBeVisible();
                    await GitExerciseParticipation.setupSSHCredentials(page.context(), sshAlgorithm);
                    await page.reload();
                    await GitExerciseParticipation.makeSubmission(
                        programmingExerciseOverview,
                        studentOne,
                        javaAllSuccessfulSubmission,
                        'Solution',
                        GitCloneMethod.ssh,
                        sshAlgorithm,
                    );
                    await programmingExerciseOverview.checkResultScore(javaAllSuccessfulSubmission.expectedResult);
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
            await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, firstSubmission.student, firstSubmission.submission, firstSubmission.commitMessage);
            await programmingExerciseOverview.checkResultScore(firstSubmission.submission.expectedResult);

            for (let i = 1; i < submissions.length; i++) {
                const { student, submission, commitMessage } = submissions[i];
                await login(student, '/');
                await page.waitForURL(/\/courses/);
                await courseList.openCourse(course.id!);
                await courseOverview.openExercise(exercise.title!);
                submission.deleteFiles = [];
                await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, student, submission, commitMessage);
                await programmingExerciseOverview.checkResultScore(submission.expectedResult);
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
                // Track files that have been created across all submissions (for team exercises, all members share the same repository)
                const createdFiles = new Set<string>();
                for (const { student, submission } of submissions) {
                    await login(student);
                    const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
                    participation = await response.json();
                    // Only create files that haven't been created yet
                    for (const file of submission.files) {
                        const filename = `src/${submission.packageName.replace(/\./g, '/')}/${file.name}`;
                        if (!createdFiles.has(filename)) {
                            await exerciseAPIRequests.createProgrammingExerciseFile(participation.id!, filename);
                            createdFiles.add(filename);
                        }
                    }
                    await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, submission);
                    // Use student-accessible endpoint (by participation ID) instead of tutor-only endpoint (by exercise ID)
                    await waitForParticipationBuildToFinish(participation.id!);
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

    test.describe('Instructor Makes a git submission to the student participation', () => {
        let exercise: ProgrammingExercise;
        let programmingLanguage = ProgrammingLanguage.JAVA;
        test.beforeEach('Setup programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage });
        });

        test('Makes a git submission through HTTPS', async ({
            login,
            navigationBar,
            courseManagement,
            courseManagementExercises,
            programmingExerciseParticipations,
            programmingExerciseOverview,
            programmingExerciseSubmissions,
        }) => {
            // student submits to create a participation + submission
            await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
            await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentOne, javaAllSuccessfulSubmission, 'student commit');
            // now instructor commits to the student participation
            await login(instructor);
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.openExerciseParticipations(exercise.id!);
            await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, instructor, javaPartiallySuccessfulSubmission, 'instructor commit');
            // check the submission
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.openExerciseParticipations(exercise.id!);
            await programmingExerciseParticipations.openStudentParticipationSubmissions(studentOne);
            // there should be both submissions
            await programmingExerciseSubmissions.checkInstructorSubmission();
            await programmingExerciseSubmissions.checkStudentSubmission();
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
