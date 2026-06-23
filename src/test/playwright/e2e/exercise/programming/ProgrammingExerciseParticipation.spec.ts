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

            test('Makes a submission using code editor', async ({ courseOverview, programmingExerciseOverview, programmingExerciseEditor }) => {
                // C builds can take longer under CI parallel load
                test.slow();
                const expectedResultPattern = ProgrammingExerciseOverviewPage.buildResultScorePattern(submission.expectedResult);
                await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
                await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, submission, async () => {
                    const resultScore = programmingExerciseEditor.getResultScoreFromExercise(exercise.id!);
                    await expect(resultScore).toContainText(expectedResultPattern, { timeout: BUILD_RESULT_TIMEOUT * 2 });
                });
                // After the build completes, the result must also surface in the course-overview sidebar card (the
                // `isInSidebarCard` placement of jhi-result), which the editor/header placements do not cover.
                await courseOverview.checkExerciseResultInSidebar(course.id!, exercise.title!, expectedResultPattern);
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

    // Verifies that the course-overview sidebar card (the `isInSidebarCard` placement of jhi-result, rendered via
    // jhi-updating-result) re-renders purely from websocket pushes — no page reload. While the student stays on the
    // overview, a fresh build is triggered via REST; the card must transition to the building indicator (submission
    // websocket) and then back to the result (result websocket). This guards the live-update path that the
    // fresh-navigate assertion above does not cover.
    test.describe('Updates the course-overview sidebar result live via websocket', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
        });

        test('Re-renders the sidebar card (building then result) on a websocket result without reloading', async ({
            page,
            courseOverview,
            programmingExerciseOverview,
            programmingExerciseEditor,
        }) => {
            test.slow();
            const expectedResultPattern = ProgrammingExerciseOverviewPage.buildResultScorePattern(cAllSuccessful.expectedResult);
            const participationId = await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, cAllSuccessful, async () => {
                await expect(programmingExerciseEditor.getResultScoreFromExercise(exercise.id!)).toContainText(expectedResultPattern, { timeout: BUILD_RESULT_TIMEOUT * 2 });
            });
            // Open the course overview and confirm the sidebar already shows the result (fresh-load path).
            await courseOverview.checkExerciseResultInSidebar(course.id!, exercise.title!, expectedResultPattern);
            // Trigger a fresh build via REST while staying on the overview. The card must update from websocket pushes
            // alone: first the building/queued indicator, then the result again — proving the live re-render.
            const card = courseOverview.getExerciseSidebarCard(exercise.title!);
            const buildingIndicator = card.locator('#test-building, #test-queued, jhi-result-progress-bar');
            // The live building/queued indicator is driven by the per-user /user/topic/newSubmissions and
            // /user/topic/submissionProcessing websocket notifications. In the multi-node cluster these user-destination
            // messages are delivered cross-node via the broker's user-destination broadcast, which is best-effort under
            // load: a single notification can be missed when the trigger-build request is processed on a different node
            // than the student's websocket session. The build (and its eventual result) always run; only the transient
            // building push may be lost. Re-trigger a fresh build until the card shows the live indicator — mirroring how
            // other tests here re-issue best-effort async backend actions (e.g. the message-search retry) — so this
            // assertion reliably observes the websocket-driven re-render instead of depending on a single push.
            let buildingObserved = false;
            for (let attempt = 0; attempt < 6 && !buildingObserved; attempt++) {
                // A transient non-2xx (the build dispatch itself can briefly fail under heavy multi-node load) is just
                // retried — only the final "never observed" outcome fails the test.
                const triggerResponse = await page.request
                    .post(`/api/programming/participations/${participationId}/trigger-build?submissionType=MANUAL`, { data: {} })
                    .catch(() => undefined);
                if (!triggerResponse?.ok()) {
                    continue;
                }
                buildingObserved = await buildingIndicator.waitFor({ state: 'visible', timeout: 20000 }).then(
                    () => true,
                    () => false,
                );
            }
            expect(buildingObserved, 'the course-overview sidebar card never showed the live building/queued indicator after re-triggering the build').toBe(true);
            // After the live building indicator, the card must transition back to the result purely from the websocket result push.
            await expect(card.locator('#result-score')).toContainText(expectedResultPattern, { timeout: BUILD_RESULT_TIMEOUT * 2 });
        });
    });

    // Reproduces tester feedback (@laadvo): when a student starts an exercise in place on the course overview, the
    // sidebar card must update live (from "Not yet started" to the started/result state) WITHOUT a page reload — it
    // previously stayed at "Not yet started" until refreshing. No build is required: the participation creation alone
    // must flip the sidebar card.
    test.describe('Updates the course-overview sidebar live when starting an exercise', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
        });

        test('Sidebar card leaves "Not yet started" right after starting (no reload)', async ({ page, login, courseOverview }) => {
            test.slow();
            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            const card = courseOverview.getExerciseSidebarCard(exercise.title!);
            await expect(card).toContainText('Not yet started', { timeout: 30000 });

            const startButton = courseOverview.getStartExerciseButton(exercise.id!);
            await startButton.waitFor({ state: 'visible', timeout: 30000 });
            const participationResponse = page.waitForResponse(
                (resp) => resp.url().includes(`/exercises/${exercise.id}/participations`) && resp.request().method() === 'POST' && resp.status() === 201,
            );
            await startButton.click();
            await participationResponse;

            // Without any reload, the sidebar card must no longer show "Not yet started".
            await expect(card).not.toContainText('Not yet started', { timeout: 15000 });
        });
    });

    // Covers the result-only ("Bucket C") placement of jhi-result in the instructor/admin build overview
    // (finished-jobs-table), where the component receives a [result] without a participation or exercise.
    // evaluateTemplateStatus short-circuits to HAS_RESULT there, so the score badge must render.
    test.describe('Shows the build result in the course build overview', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
        });

        test('Renders the result-only badge in the finished build jobs table', async ({ login, page, programmingExerciseOverview, programmingExerciseEditor }) => {
            test.slow();
            const expectedResult = cAllSuccessful.expectedResult;
            const expectedResultPattern = ProgrammingExerciseOverviewPage.buildResultScorePattern(expectedResult);
            await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, cAllSuccessful, async () => {
                await expect(programmingExerciseEditor.getResultScoreFromExercise(exercise.id!)).toContainText(expectedResultPattern, { timeout: BUILD_RESULT_TIMEOUT * 2 });
            });
            // As an instructor, open the course build overview; the finished-jobs table renders jhi-result with a
            // result-only input. The build just produced a passing result, so a matching score badge must be visible.
            await login(instructor);
            await page.goto(`/course-management/${course.id}/build-overview`);
            await page.waitForLoadState('domcontentloaded');
            await expect(page.locator('jhi-result #result-score').filter({ hasText: expectedResult }).first()).toBeVisible({ timeout: 30000 });
        });
    });
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
                    // SSH-setup happens in a separate page context, then we reload the main
                    // page. Under heavy multi-node CI load the reload-vs-SSH-key-registration
                    // race occasionally lands the main page on /courses (Angular's
                    // auth/router fall-back) instead of the exercise route. Navigate to the
                    // exercise URL explicitly so the subsequent `makeSubmission` reliably
                    // finds `.code-button`. A direct goto is idempotent — it costs ~200ms on
                    // the happy path and eliminates the drift-recovery branch.
                    const exerciseUrl = `/courses/${course.id}/exercises/${exercise.id!}`;
                    await GitExerciseParticipation.setupSSHCredentials(page.context(), sshAlgorithm);
                    await page.goto(exerciseUrl);
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
                // The shared beforeEach runs 3 student submissions and waits for 3 C builds,
                // which alone consumes most of the default slow-test budget under CI load.
                // `test.slow()` triples the budget so the verification steps still fit.
                test.slow();
                await login(instructor, '/');
                await navigationBar.openCourseManagement();
                await courseManagement.openExercisesOfCourse(course.id!);
                await courseManagementExercises.openExerciseParticipations(exercise.id!);
                // The participations table fills asynchronously after navigation; give it a
                // generous timeout because under heavy parallel load both the API response
                // and Angular's grid-render can be slow.
                await programmingExerciseParticipations.getParticipation(team.name!).waitFor({ state: 'visible', timeout: 60_000 });
                await programmingExerciseParticipations.checkParticipationTeam(team.name!, team.name!);
                const studentUsernames = submissions.map(({ student }) => student.username!);
                await programmingExerciseParticipations.checkParticipationStudents(team.name!, studentUsernames);
                const programmingExerciseRepository = await programmingExerciseParticipations.openRepositoryOnNewPage(team.name!);
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
            // Under multi-node CI load the course-management lazy chunk occasionally fails to
            // resolve and Angular's auth/router fall-back lands the page on /courses instead.
            // Verify the URL after navigation and re-issue the goto if it drifted — without
            // this the subsequent `openCloneMenu` reloads /courses forever and times out.
            const participationsUrl = `/course-management/${course.id}/programming-exercises/${exercise.id!}/participations`;
            const expectedParticipationsUrl = new RegExp(`/course-management/${course.id}/programming-exercises/${exercise.id!}/participations(?:[/?#].*)?$`);
            const gotoParticipations = async () => {
                for (let attempt = 0; attempt < 2; attempt++) {
                    await page.goto(participationsUrl);
                    const settled = await page.waitForURL(expectedParticipationsUrl, { timeout: 15000 }).then(
                        () => true,
                        () => false,
                    );
                    if (settled) {
                        return;
                    }
                }
                throw new Error(`Failed to navigate to participations page after 2 attempts — landed on ${page.url()}`);
            };
            await gotoParticipations();
            await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, instructor, cPartiallySuccessfulSubmission, 'instructor commit');
            await gotoParticipations();
            await programmingExerciseParticipations.openStudentParticipationSubmissions(studentOne);
            await programmingExerciseSubmissions.checkInstructorSubmission(60000);
            await programmingExerciseSubmissions.checkStudentSubmission();
        });
    });
});
