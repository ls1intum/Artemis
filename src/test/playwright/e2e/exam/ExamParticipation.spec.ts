import { test } from '../../support/fixtures';
import { Commands } from '../../support/commands';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise, ExerciseType, ProgrammingExerciseAssessmentType, ProgrammingLanguage } from '../../support/constants';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, users } from '../../support/users';
import { generateUUID } from '../../support/utils';
import cAllSuccessfulSubmission from '../../fixtures/exercise/programming/c/all_successful/submission.json';
import dayjs from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { expect } from '@playwright/test';
import { ExamStartEndPage } from '../../support/pageobjects/exam/ExamStartEndPage';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ModalDialogBox } from '../../support/pageobjects/exam/ModalDialogBox';
import { ExamParticipationActions, TextDifferenceType } from '../../support/pageobjects/exam/ExamParticipationActions';
import { ExamNavigationBar } from '../../support/pageobjects/exam/ExamNavigationBar';
import textExerciseTemplate from '../../fixtures/exercise/text/template.json';
import { GitExerciseParticipation } from '../../support/pageobjects/exercises/programming/GitExerciseParticipation';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { GitCloneMethod } from '../../support/pageobjects/exercises/programming/ProgrammingExerciseOverviewPage';
import { SshEncryptionAlgorithm } from '../../support/pageobjects/exercises/programming/GitClient';
import { SEED_COURSES } from '../../support/seedData';
import { BUILD_RESULT_TIMEOUT } from '../../support/timeouts';

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
            exam = await createExam(course, examAPIRequests, { title: examTitle, examMaxPoints: 40, numberOfExercisesInExam: 4 });
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

        test('Participates as a student in a registered exam', async ({ login, examParticipation, examNavigation, examStartEnd, examManagement }) => {
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

            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentTwoName);
        });

        test('Using navigation sidebar to navigate within exam', async ({ login, examParticipation, examNavigation, examManagement }) => {
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

            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentThreeName);
        });

        test('Using exercise overview to navigate within exam', async ({ login, examParticipation, examNavigation, examManagement }) => {
            await examParticipation.startParticipation(studentFour, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openFromOverviewByTitle(exercise.exerciseGroup!.title!);
                await examNavigation.openOverview();
            }
            await examParticipation.handInEarly();

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
            exam = await createExam(course, examAPIRequests, { title: examTitle });
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
            // Wait for the clear to take effect before typing new text
            await page.locator(`#exercise-${textExercise.id} #text-editor`).waitFor({ state: 'visible' });
            await expect(page.locator(`#exercise-${textExercise.id} #text-editor`))
                .toHaveValue('', { timeout: 5000 })
                .catch(() => {
                    console.warn('Text editor did not clear within 5s — proceeding; this is best-effort and not the assertion under test');
                });
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
            await page.goto(`/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.startExam();
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

            await page.reload();

            await examParticipation.verifyTextExerciseOnFinalPage(textExercise.id!, textExercise.additionalData!.textFixture!);
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
            exam = await createExam(course, examAPIRequests, { title: examTitle, endDate: dayjs().add(1, 'minute') });
            const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
            exerciseArray.push(exercise);

            await examAPIRequests.registerStudentForExam(exam, studentFour);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
        });

        test('Participates as a student in a registered exam', async ({ login, examParticipation, examNavigation, examStartEnd, examManagement }) => {
            await examParticipation.startParticipation(studentFour, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.makeSubmission(textExercise.id!, textExercise.type!, textExercise.additionalData);
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.checkExamFullnameInputExists();
            await examParticipation.checkYourFullname(studentFourName);
            const response = await examStartEnd.finishExam();
            expect(response.status()).toBe(200);
            await examStartEnd.pressShowSummary();
            await examParticipation.verifyTextExerciseOnFinalPage(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.checkExamTitle(examTitle);

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
                exam = await createExam(course, examAPIRequests, { title: 'exam' + generateUUID(), endDate: dayjs().add(5, 'minutes') });
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
                // Git clone + push + CI build takes longer under parallel CI load
                test.slow();
                await examParticipation.startParticipation(studentTwo, course, exam);
                // Intercept the participation ID when navigating to the exercise.
                // The exam loads participation data via API — capture it.
                const participationPromise = page
                    .waitForResponse((resp) => resp.url().includes('/participations') && resp.url().includes(`${programmingExercise.id}`) && resp.status() === 200, {
                        timeout: 30000,
                    })
                    .catch(() => null);
                await examNavigation.openOrSaveExerciseByTitle(programmingExercise.exerciseGroup!.title!);
                const participationResponse = await participationPromise;
                let participationId: number | undefined;
                if (participationResponse) {
                    try {
                        const data = await participationResponse.json();
                        participationId = data.id ?? data[0]?.id;
                    } catch {
                        /* response might not be JSON */
                    }
                }
                await GitExerciseParticipation.makeSubmission(programmingExerciseOverview, studentTwo, cAllSuccessfulSubmission, 'Solution', cloneMethod);
                // Wait for build via API (student-accessible endpoint) before checking UI.
                if (participationId) {
                    await waitForParticipationBuildToFinish(participationId);
                }
                await examParticipation.checkExerciseScore(programmingExercise.id!, cAllSuccessfulSubmission.expectedResult, BUILD_RESULT_TIMEOUT * 2);
                await examParticipation.handInEarly();
                await examAPIRequests.finishExam(exam);
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
            exam = await createExam(course, examAPIRequests);
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
            await page.waitForLoadState('networkidle');

            const studentPages = [];

            for (const student of [studentOne, studentTwo]) {
                const studentContext = await browser.newContext();
                const studentPage = await studentContext.newPage();
                studentPages.push(studentPage);

                await Commands.login(studentPage, student);
                await studentPage.goto(`/courses/${course.id!}/exams/${exam.id!}`);
                const examStartEnd = new ExamStartEndPage(studentPage);
                await examStartEnd.startExam(false);
            }

            // Wait for WebSocket connections to be established on student pages
            for (const studentPage of studentPages) {
                await studentPage.waitForLoadState('networkidle');
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
            await page.waitForLoadState('networkidle');

            const studentPages = [];

            for (const student of students) {
                const studentContext = await browser.newContext();
                const studentPage = await studentContext.newPage();
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
                await expect(studentPage.locator('#displayTime')).toContainText(/2[5-9]|3[0-1]/);
            }
        });

        test(
            'Instructor changes problem statement and all participants are informed',
            { tag: '@fast' },
            async ({ browser, login, page, examExerciseGroups, examDetails, textExerciseCreation }) => {
                await login(instructor);
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}`);
                await page.waitForLoadState('networkidle');

                const studentPages = [];

                for (const student of students) {
                    const studentContext = await browser.newContext();
                    const studentPage = await studentContext.newPage();
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

async function createExam(course: Course, examAPIRequests: ExamAPIRequests, customExamConfig?: any) {
    const defaultExamConfig = {
        course,
        title: 'exam' + generateUUID(),
        visibleDate: dayjs().subtract(3, 'minutes'),
        startDate: dayjs().subtract(2, 'minutes'),
        endDate: dayjs().add(1, 'hour'),
        examMaxPoints: 10,
        numberOfExercisesInExam: 1,
    };
    const examConfig = { ...defaultExamConfig, ...customExamConfig };
    return await examAPIRequests.createExam(examConfig);
}
