import { test } from '../../support/fixtures';
import { Commands } from '../../support/commands';
import { Exercise, ExerciseType, ProgrammingExerciseAssessmentType, ProgrammingLanguage } from '../../support/constants';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, users } from '../../support/users';
import { addE2EInitScript, asAdmin, generateUUID } from '../../support/utils';
import cAllSuccessfulSubmission from '../../fixtures/exercise/programming/c/all_successful/submission.json';
import dayjs from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { expectStoredAnswers } from '../../support/examAnswerAssertions';
import { expect } from '@playwright/test';
import { ExamStartEndPage } from '../../support/pageobjects/exam/ExamStartEndPage';
import { ModalDialogBox } from '../../support/pageobjects/exam/ModalDialogBox';
import { ExamParticipationActions, TextDifferenceType } from '../../support/pageobjects/exam/ExamParticipationActions';
import { ExamNavigationBar } from '../../support/pageobjects/exam/ExamNavigationBar';
import textExerciseTemplate from '../../fixtures/exercise/text/template.json';
import { GitExerciseParticipation } from '../../support/pageobjects/exercises/programming/GitExerciseParticipation';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { GitCloneMethod } from '../../support/pageobjects/exercises/programming/ProgrammingExerciseOverviewPage';
import { SshEncryptionAlgorithm } from '../../support/pageobjects/exercises/programming/GitClient';
import { SEED_COURSES } from '../../support/seedData';
import { BUILD_RESULT_TIMEOUT, RELOAD_RENDER_TIMEOUT } from '../../support/timeouts';

// Common primitives
const textFixture = 'loremIpsum.txt';
const textFixtureShort = 'loremIpsum-short.txt';
const course = { id: SEED_COURSES.examParticipation.id } as any;

test.describe('Exam participation', () => {
    let exerciseArray: Array<Exercise> = [];
    let studentTwoName: string;
    let studentThreeName: string;
    let studentFourName: string;

    test.beforeEach('Get user names', async ({ login, page }) => {
        await login(admin);

        const studentTwoInfo = await users.getUserInfo(studentTwo.username, page);
        studentTwoName = studentTwoInfo.name!;

        const studentThreeInfo = await users.getUserInfo(studentThree.username, page);
        studentThreeName = studentThreeInfo.name!;

        const studentFourInfo = await users.getUserInfo(studentFour.username, page);
        studentFourName = studentFourInfo.name!;
    });

    test.describe('Early Hand-in', { tag: '@slow' }, () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
            await login(admin);
            exam = await examAPIRequests.createRunningExam({ course, title: examTitle, examMaxPoints: 40, numberOfExercisesInExam: 4 });
            const textExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
            const programmingExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, {
                submission: cAllSuccessfulSubmission,
                programmingLanguage: ProgrammingLanguage.C,
                // This test checks the exam workflow (start → submit → hand-in), not build timing.
                // Waiting for the C build result (can exceed 3 min under CI load) would fail the test.
                skipBuildResultCheck: true,
            });
            const quizExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 });
            const modelingExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING);
            exerciseArray = [textExercise, programmingExercise, quizExercise, modelingExercise];

            await examAPIRequests.registerStudentForExam(exam, studentTwo);
            await examAPIRequests.registerStudentForExam(exam, studentThree);
            await examAPIRequests.registerStudentForExam(exam, studentFour);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
        });

        test('Participates as a student in a registered exam', async ({ login, examAPIRequests, examParticipation, examNavigation, examStartEnd, examManagement }) => {
            // Submits 4 exercise types including programming (build takes 30-60s under load)
            test.slow();
            await examParticipation.startParticipation(studentTwo, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
                await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
            }
            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examParticipation.verifyExerciseTitleOnFinalPage(exercise.id!, exercise.exerciseGroup!.title!);
                if (exercise.type === ExerciseType.TEXT) {
                    await examParticipation.verifyTextExerciseOnFinalPage(exercise.id!, exercise.additionalData!.textFixture!);
                }
            }
            await examParticipation.checkExamTitle(examTitle);

            // What the summary shows is what the server stored: every exercise, with the answer the student gave.
            const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
            expect(summary.submitted).toBe(true);
            expect(summary.exercises).toHaveLength(exerciseArray.length);
            await expectStoredAnswers(summary, exerciseArray, { [ExerciseType.TEXT]: true, [ExerciseType.QUIZ]: true, [ExerciseType.MODELING]: true });

            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentTwoName);
        });

        test('Using navigation sidebar to navigate within exam', async ({ login, examAPIRequests, examParticipation, examNavigation, examStartEnd, examManagement }) => {
            await examParticipation.startParticipation(studentThree, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type !== ExerciseType.PROGRAMMING) {
                    await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
                    await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
                }
            }
            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();

            // Saving through the sidebar stored the answers of the exercises that were worked on; the programming exercise was only opened.
            const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
            expect(summary.submitted).toBe(true);
            expect(summary.exercises).toHaveLength(exerciseArray.length);
            await expectStoredAnswers(summary, exerciseArray, { [ExerciseType.TEXT]: true, [ExerciseType.QUIZ]: true, [ExerciseType.MODELING]: true });

            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentThreeName);
        });

        test('Using exercise overview to navigate within exam', async ({ login, examAPIRequests, examParticipation, examNavigation, examStartEnd, examManagement }) => {
            await examParticipation.startParticipation(studentFour, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openFromOverviewByTitle(exercise.exerciseGroup!.title!);
                await examNavigation.openOverview();
            }
            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();

            // Only browsing through the exercises must neither lose nor invent answers: all exercises are there, none has an answer.
            const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
            expect(summary.submitted).toBe(true);
            expect(summary.exercises).toHaveLength(exerciseArray.length);
            await expectStoredAnswers(summary, exerciseArray, { [ExerciseType.TEXT]: false, [ExerciseType.QUIZ]: false, [ExerciseType.MODELING]: false });

            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentFourName);
        });

        test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
            await login(admin);
            await examAPIRequests.deleteExam(exam);
        });
    });

    test.describe('Early hand-in with continue and reload page', { tag: '@slow' }, () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
            exerciseArray = [];

            await login(admin);
            exam = await examAPIRequests.createRunningExam({ course, title: examTitle });
            const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
            exerciseArray.push(exercise);

            await examAPIRequests.registerStudentForExam(exam, studentTwo);
            await examAPIRequests.registerStudentForExam(exam, studentThree);
            await examAPIRequests.registerStudentForExam(exam, studentFour);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
        });

        test('Participates in the exam, hand-in early, but instead continues', async ({
            page,
            login,
            examParticipation,
            examNavigation,
            examStartEnd,
            textExerciseEditor,
            examManagement,
        }) => {
            await examParticipation.startParticipation(studentTwo, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.makeTextExerciseSubmission(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examNavigation.handInEarly();

            await examStartEnd.clickContinue();
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await textExerciseEditor.clearSubmission(textExercise.id!);
            // The old answer must really be gone, on screen and in the client's copy that the character badge is rendered from.
            await expect(page.locator(`#exercise-${textExercise.id} #text-editor`)).toHaveValue('');
            await expect(page.locator(`#exercise-${textExercise.id}`).getByTestId('character-count')).toContainText('0');
            await examParticipation.makeTextExerciseSubmission(textExercise.id!, textFixtureShort);
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);

            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();
            await examParticipation.verifyTextExerciseOnFinalPage(textExercise.id!, textFixtureShort);
            await examParticipation.checkExamTitle(examTitle);

            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentTwoName);
        });

        test('Reloads exam page during participation and ensures that everything is as expected', async ({
            page,
            login,
            examParticipation,
            examNavigation,
            textExerciseEditor,
            examStartEnd,
            examManagement,
        }) => {
            await examParticipation.startParticipation(studentThree, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.makeTextExerciseSubmission(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);

            await page.reload();
            // A reload must not send a student who already started back to the welcome screen: the exam resumes, with its timer running.
            await expect(page.getByTestId('hand-in-early')).toBeVisible({ timeout: RELOAD_RENDER_TIMEOUT });
            await expect(page.locator('#confirmBox')).toHaveCount(0);
            await expect(page.getByTestId('exam-bar-title')).toContainText(examTitle);
            await expect(page.getByTestId('displayTime')).toHaveText(/\S/);
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await textExerciseEditor.checkCurrentContent(textExercise.additionalData!.textFixture!);
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();
            await examParticipation.verifyTextExerciseOnFinalPage(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.checkExamTitle(examTitle);

            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentThreeName);
        });

        test('Reloads exam result page and ensures that everything is as expected', async ({ page, login, examParticipation, examNavigation, examStartEnd, examManagement }) => {
            // Full exam-participation flow (startParticipation → submit → hand-in → summary →
            // reload + re-verify → instructor verifySubmitted) plus the post-reload toHaveValue
            // wait can exceed the 90s @slow budget when the conduction view lazy-chunks slowly.
            // Lift to 270s via test.slow() — observed worst case ~140s.
            test.slow();
            await examParticipation.startParticipation(studentFour, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.makeTextExerciseSubmission(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();
            await examParticipation.verifyTextExerciseOnFinalPage(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.checkExamTitle(examTitle);

            // Reload through the route-restoring helper rather than page.reload() directly. A reload re-bootstraps the
            // SPA, and when a lazy route chunk fails to resolve behind the multi-node HTTPS load balancer the router
            // drops to the /courses fallback and never returns. The summary is then not on screen, and the assertions
            // below wait for content that cannot appear - which is how this test failed on develop. Restoring the route
            // turns that dead end into one more attempt, and reports honestly when the SPA could not load the route.
            const summaryUrl = page.url();
            const restoredSummary = await Commands.reloadAndRestoreRoute(page, summaryUrl);
            expect(restoredSummary, 'the exam summary route did not survive the reload').toBe(true);

            // First assertion after the reload still absorbs the full client re-bootstrap: the summary view lazy-loads
            // its chunks, so the default 10s expect timeout is not enough under parallel CI load. checkExamTitle
            // renders in the same pass and keeps the default.
            await examParticipation.verifyTextExerciseOnFinalPage(textExercise.id!, textExercise.additionalData!.textFixture!, RELOAD_RENDER_TIMEOUT);
            await examParticipation.checkExamTitle(examTitle);

            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentFourName);
        });

        test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
            await login(admin);
            await examAPIRequests.deleteExam(exam);
        });
    });

    test.describe('Normal Hand-in', { tag: '@slow' }, () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
            exerciseArray = [];

            await login(admin);
            exam = await examAPIRequests.createRunningExam({ course, title: examTitle });
            const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
            exerciseArray.push(exercise);

            await examAPIRequests.registerStudentForExam(exam, studentFour);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
        });

        test('Participates as a student in a registered exam', async ({
            browser,
            page,
            login,
            examAPIRequests,
            examParticipation,
            examNavigation,
            examStartEnd,
            examManagement,
        }) => {
            await examParticipation.startParticipation(studentFour, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.makeSubmission(textExercise.id!, textExercise.type!, textExercise.additionalData);
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);

            // The student works until the exam runs out: its deadline is set now that the student is working, so a slow setup cannot cut it short.
            await asAdmin(browser, (adminExamRequests) => adminExamRequests.endExamIn(exam, 20));
            const workingTimeDialog = new ModalDialogBox(page);
            await workingTimeDialog.checkDialogMessage('The working time of the exam has been changed.');
            await workingTimeDialog.closeDialog();
            await examParticipation.checkExamFullnameInputExists();
            await examParticipation.checkYourFullname(studentFourName);
            const response = await examStartEnd.finishExam();
            expect(response.status()).toBe(200);
            await examStartEnd.pressShowSummary();
            await examParticipation.verifyTextExerciseOnFinalPage(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.checkExamTitle(examTitle);

            // The exam ended on its own and the server stored the answer the student gave before that.
            const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
            expect(summary.submitted).toBe(true);
            await expectStoredAnswers(summary, exerciseArray, { [ExerciseType.TEXT]: true });

            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentFourName);
        });

        test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
            await login(admin);
            await examAPIRequests.deleteExam(exam);
        });
    });

    for (const cloneMethod of [GitCloneMethod.https, GitCloneMethod.httpsWithToken, GitCloneMethod.ssh]) {
        test.describe('Programming exam with Git submissions', { tag: '@slow' }, () => {
            let exam: Exam;
            let programmingExercise: ProgrammingExercise;

            test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
                await login(admin);
                exam = await examAPIRequests.createRunningExam({ course, title: 'exam' + generateUUID(), endDate: dayjs().add(5, 'minutes') });
                const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, {
                    submission: cAllSuccessfulSubmission,
                    progExerciseAssessmentType: ProgrammingExerciseAssessmentType.AUTOMATIC,
                    programmingLanguage: ProgrammingLanguage.C,
                });
                programmingExercise = exercise as ProgrammingExercise;

                await examAPIRequests.registerStudentForExam(exam, studentTwo);
                await examAPIRequests.generateMissingIndividualExams(exam);
                await examAPIRequests.prepareExerciseStartForExam(exam);
            });

            if (cloneMethod === GitCloneMethod.ssh) {
                test.beforeEach('Setup SSH credentials', async ({ page, login }) => {
                    await login(studentTwo);
                    await GitExerciseParticipation.setupSSHCredentials(page.context(), SshEncryptionAlgorithm.ed25519);
                    await page.reload();
                });
            }

            test(`Participates in exam by Git submission using ${cloneMethod}`, async ({
                page,
                login,
                examAPIRequests,
                examParticipation,
                examNavigation,
                programmingExerciseOverview,
                examManagement,
                waitForParticipationBuildToFinish,
            }) => {
                // Git clone + push + CI build takes longer under parallel CI load.
                test.slow();
                await examParticipation.startParticipation(studentTwo, course, exam);
                await examNavigation.openOrSaveExerciseByTitle(programmingExercise.exerciseGroup!.title!);
                const participationId = await examAPIRequests.getOwnParticipationId(exam, programmingExercise.id!);
                await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentTwo, cAllSuccessfulSubmission, 'Solution', cloneMethod);
                // Wait for build via API (student-accessible endpoint) before checking UI.
                await waitForParticipationBuildToFinish(participationId);
                await examParticipation.checkExerciseScore(programmingExercise.id!, 'Build successful, no tests executed', BUILD_RESULT_TIMEOUT * 2);
                await examParticipation.handInEarly();
                await login(instructor);
                await examManagement.verifySubmitted(course.id!, exam.id!, studentTwoName);
            });

            if (cloneMethod === GitCloneMethod.ssh) {
                test.afterEach('Delete SSH key', async ({ login, accountManagementAPIRequests }) => {
                    await login(studentTwo);
                    await accountManagementAPIRequests.deleteSshPublicKey();
                });
            }

            test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
                await login(admin);
                await examAPIRequests.deleteExam(exam);
            });
        });
    }

    test.describe('Exam announcements', () => {
        let exam: Exam;
        const students = [studentOne, studentTwo];
        let exercise: Exercise;

        test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
            await login(admin);
            exam = await examAPIRequests.createRunningExam({ course });
            exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
            exerciseArray.push(exercise);
            for (const student of students) {
                await examAPIRequests.registerStudentForExam(exam, student);
            }

            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
        });

        test('Instructor sends an announcement message and all participants receive it', { tag: '@slow' }, async ({ browser, login, page, examManagement }) => {
            await login(instructor);
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}`);
            await page.waitForLoadState('domcontentloaded');

            const studentPages = [];

            for (const student of [studentOne, studentTwo]) {
                const studentContext = await browser.newContext();
                const studentPage = await studentContext.newPage();
                await addE2EInitScript(studentPage);
                studentPages.push(studentPage);

                await Commands.login(studentPage, student);
                await studentPage.goto(`/courses/${course.id!}/exams/${exam.id!}`);
                const examStartEnd = new ExamStartEndPage(studentPage);
                await examStartEnd.startExam(false);
            }

            // Wait for WebSocket connections to be established on student pages
            for (const studentPage of studentPages) {
                await studentPage.waitForLoadState('domcontentloaded');
            }

            const announcement = 'Important announcement!';
            await examManagement.openAnnouncementDialog();
            const announcementTypingTime = dayjs();
            await examManagement.typeAnnouncementMessage(announcement);
            await examManagement.verifyAnnouncementContent(announcementTypingTime, announcement, instructor.username);
            await examManagement.sendAnnouncement();

            for (const studentPage of studentPages) {
                const modalDialog = new ModalDialogBox(studentPage);
                await modalDialog.checkDialogTime(announcementTypingTime);
                await modalDialog.checkDialogMessage(announcement);
                await modalDialog.closeDialog();
            }
        });

        test('Instructor changes working time and all participants are informed', { tag: '@slow' }, async ({ browser, login, page, examManagement }) => {
            await login(instructor);
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}`);
            await page.waitForLoadState('domcontentloaded');

            const studentPages = [];

            for (const student of students) {
                const studentContext = await browser.newContext();
                const studentPage = await studentContext.newPage();
                await addE2EInitScript(studentPage);
                studentPages.push(studentPage);

                await Commands.login(studentPage, student);
                await studentPage.goto(`/courses/${course.id!}/exams/${exam.id!}`);
                const examStartEnd = new ExamStartEndPage(studentPage);
                await examStartEnd.startExam(false);
            }

            await examManagement.openEditWorkingTimeDialog();
            await examManagement.changeExamWorkingTime({ minutes: -30 });
            await examManagement.verifyExamWorkingTimeChange('1h 2min', '32min');
            const workingTimeChangeTime = dayjs();
            await examManagement.confirmWorkingTimeChange(exam.title!);

            for (const studentPage of studentPages) {
                const modalDialog = new ModalDialogBox(studentPage);
                const timeChangeMessage = 'The working time of the exam has been changed.';
                await modalDialog.checkExamTimeChangeDialog('1h 2min', '32min');
                await modalDialog.checkDialogTime(workingTimeChangeTime);
                await modalDialog.checkDialogMessage(timeChangeMessage);
                await modalDialog.closeDialog();
                // After reducing working time by 30min (from 1h2min to 32min), verify timer shows ~25-31min remaining
                await expect(studentPage.locator('[data-testid="displayTime"]')).toContainText(/2[5-9]|3[0-1]/);
            }
        });

        test(
            'Instructor changes problem statement and all participants are informed',
            { tag: '@fast' },
            async ({ browser, login, page, examExerciseGroups, examDetails, textExerciseCreation }) => {
                await login(instructor);
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}`);
                await page.waitForLoadState('domcontentloaded');

                const studentPages = [];

                for (const student of students) {
                    const studentContext = await browser.newContext();
                    const studentPage = await studentContext.newPage();
                    await addE2EInitScript(studentPage);
                    studentPages.push(studentPage);

                    await Commands.login(studentPage, student);
                    await studentPage.goto(`/courses/${course.id!}/exams/${exam.id!}`);
                    const examStartEnd = new ExamStartEndPage(studentPage);
                    await examStartEnd.startExam(false);
                    const examNavigation = new ExamNavigationBar(studentPage);
                    await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
                }

                await examDetails.openExerciseGroups();
                await examExerciseGroups.clickEditExercise(exercise.exerciseGroup!.id!, exercise.id!);

                const problemStatementText = textExerciseTemplate.problemStatement;
                const startOfChangesIndex = problemStatementText.lastIndexOf(' ') + 1;
                const removedText = problemStatementText.slice(startOfChangesIndex);
                const unchangedText = problemStatementText.slice(0, startOfChangesIndex);
                const addedText = 'Changed';
                await textExerciseCreation.clearProblemStatement();
                await textExerciseCreation.typeProblemStatement(unchangedText + addedText);
                await textExerciseCreation.create();

                for (const studentPage of studentPages) {
                    const modalDialog = new ModalDialogBox(studentPage);
                    const exerciseUpdateMessage = `The problem statement of the exercise '${exercise.exerciseGroup!.title!}' was updated. Please open the exercise to see the changes.`;
                    await modalDialog.checkDialogType('Problem Statement Update');
                    await modalDialog.checkDialogMessage(exerciseUpdateMessage);
                    await modalDialog.pressModalButton('Navigate to exercise');
                    const examParticipationActions = new ExamParticipationActions(studentPage);
                    await examParticipationActions.checkExerciseProblemStatementDifference([
                        { text: unchangedText, differenceType: TextDifferenceType.NONE },
                        { text: removedText, differenceType: TextDifferenceType.DELETE },
                        { text: addedText, differenceType: TextDifferenceType.ADD },
                    ]);
                    await studentPage.locator('#highlightDiffButton').click();
                    await examParticipationActions.checkExerciseProblemStatementDifference([{ text: unchangedText + addedText, differenceType: TextDifferenceType.NONE }]);
                }
            },
        );

        test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
            await login(admin);
            await examAPIRequests.deleteExam(exam);
        });
    });
});
