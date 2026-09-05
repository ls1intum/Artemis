import dayjs, { Dayjs } from 'dayjs';
import { Exercise, ExerciseType } from '../../support/constants';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor, users } from '../../support/users';
import { Page, expect } from '@playwright/test';

import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExamManagementPage } from '../../support/pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from '../../support/pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from '../../support/pageobjects/assessment/ExerciseAssessmentDashboardPage';
import { StudentAssessmentPage } from '../../support/pageobjects/assessment/StudentAssessmentPage';
import { ExamAssessmentPage } from '../../support/pageobjects/assessment/ExamAssessmentPage';
import { test } from '../../support/fixtures';
import { generateUUID, newBrowserPage, prepareExam, startAssessing, waitForExamBuildAndTestAfterDueDate, waitForExamEnd } from '../../support/utils';
import { EXAM_DASHBOARD_TIMEOUT } from '../../support/timeouts';
import examStatisticsSample from '../../fixtures/exam/statistics.json';
import { ExamScoresPage } from '../../support/pageobjects/exam/ExamScoresPage';
import { SEED_COURSES } from '../../support/seedData';

const course = { id: SEED_COURSES.examAssessment.id } as any;
let studentOneName: string;

test.beforeAll('Get student name', async ({ browser }) => {
    const page = await newBrowserPage(browser);
    await Commands.login(page, admin);
    studentOneName = (await users.getUserInfo(studentOne.username, page)).name!;
});

test.describe('Exam assessment', () => {
    test.describe.serial('Programming exercise assessment', { tag: '@slow' }, () => {
        // Preparing an exam and initial participation can exceed 90s on loaded local CI-like runs
        // (the C template repository clone dominates). The submission step itself no longer waits
        // for a build result during setup — `prepareExam` passes `skipBuildResultCheck: true` and
        // `OnlineEditorPage.submit` now honours it — so the build queue can no longer push the hook
        // past its budget; the score-producing build is triggered later via
        // `waitForExamBuildAndTestAfterDueDate`.
        test.describe.configure({ timeout: 180_000 });
        let exam: Exam;
        let examEnd: Dayjs;

        test.beforeAll('Prepare exam', async ({ browser }) => {
            // 180s window (was 60s): programming exercise creation involves cloning a C
            // template repository, which routinely takes 30-60s under multi-node CI load.
            // The student must finish startParticipation + handInEarly inside this window
            // — at 60s, setup occasionally overran the exam end and the conduction page
            // redirected, leaving `[data-testid="hand-in-early"]` un-clickable. 180s leaves comfortable
            // headroom; the test still doesn't wait the full window since
            // `waitForExamEnd` returns once the exam ends.
            examEnd = dayjs().add(180, 'seconds');
            const page = await newBrowserPage(browser);
            exam = await prepareExam(course, examEnd, ExerciseType.PROGRAMMING, page, 2);
        });

        test('Assess a programming exercise submission (MANUAL)', async ({
            page,
            login,
            examManagement,
            examAssessment,
            examParticipation,
            courseAssessment,
            exerciseAssessment,
        }) => {
            test.slow();
            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            await waitForExamEnd(exam, page);
            await waitForExamBuildAndTestAfterDueDate(exam, page);
            await login(tutor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
            await examAssessment.addNewFeedback(2, 'Good job');
            // Assert the status, as the second-round test below already does. A rejected submit used to be
            // discarded here and only surfaced ten seconds later as a wrong result score, which pointed at
            // grading rather than at the submit that never landed.
            const assessmentResponse = await examAssessment.submit();
            expect(assessmentResponse.status()).toBe(200);
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('70%');
        });

        test('Instructor makes a second round of assessment', async ({ login, examManagement, examAssessment, examParticipation, courseAssessment, exerciseAssessment }) => {
            await login(instructor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment, true, true);
            // The second round starts from a copy of the first one, so the unreferenced feedback the tutor left is
            // already there and gets a new value instead of being added again.
            await examAssessment.fillFeedback(4, 'Better than it looks');
            const response = await examAssessment.submit();
            expect(response.status()).toBe(200);
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('90%');
        });

        test('Complaints about programming exercises assessment', async ({ examAssessment, page, studentAssessment, examManagement, courseAssessment, exerciseAssessment }) => {
            await handleComplaint(
                course,
                exam,
                false,
                ExerciseType.PROGRAMMING,
                page,
                studentAssessment,
                examManagement,
                examAssessment,
                courseAssessment,
                exerciseAssessment,
                false,
            );
        });

        test.afterAll('Delete exam', async ({ browser }) => {
            const page = await newBrowserPage(browser);
            await Commands.login(page, admin);
            await new ExamAPIRequests(page).deleteExam(exam);
            await page.close();
        });
    });

    test.describe.serial('Modeling exercise assessment', { tag: '@slow' }, () => {
        let exam: Exam;
        let examEnd: Dayjs;

        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(30, 'seconds');
            const page = await newBrowserPage(browser);
            exam = await prepareExam(course, examEnd, ExerciseType.MODELING, page, 2);
        });

        test('Assess a modeling exercise submission', async ({
            page,
            login,
            examManagement,
            modelingExerciseAssessment,
            examAssessment,
            examParticipation,
            courseAssessment,
            exerciseAssessment,
        }) => {
            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            await waitForExamEnd(exam, page);
            await login(tutor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
            await modelingExerciseAssessment.addNewFeedback(5, 'Good');
            await modelingExerciseAssessment.openAssessmentForComponent(0);
            await modelingExerciseAssessment.assessComponent(-1, 'Wrong');
            await modelingExerciseAssessment.clickNextAssessment();
            await modelingExerciseAssessment.assessComponent(0, 'Neutral');
            await modelingExerciseAssessment.clickNextAssessment();
            const response = await examAssessment.submitModelingAssessment();
            expect(response.status()).toBe(200);
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);

            await examParticipation.checkResultScore('40%');
        });

        test('Instructor makes a second round of assessment', async ({
            login,
            examManagement,
            modelingExerciseAssessment,
            examAssessment,
            examParticipation,
            courseAssessment,
            exerciseAssessment,
        }) => {
            await login(instructor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment, true, true);
            // The component assessments come first: they wait for the Apollon editor, and the editor re-renders once the
            // server data for the round arrives, which discards anything typed into the feedback form before that. The
            // second round starts from a copy of the first one, so the unreferenced feedback already exists and gets a
            // new value rather than being added again. 7 unreferenced plus -1 and 0 on the components is 6 of 10 points.
            await modelingExerciseAssessment.openAssessmentForComponent(0);
            await modelingExerciseAssessment.assessComponent(-1, 'Still wrong');
            await modelingExerciseAssessment.clickNextAssessment();
            await modelingExerciseAssessment.assessComponent(0, 'Neutral');
            await modelingExerciseAssessment.clickNextAssessment();
            await modelingExerciseAssessment.fillFeedback(7, 'Better than it looks');
            // Submitted through the exam page object, like the first round: it accepts the confirmation dialog the
            // editor raises. Playwright dismisses a dialog nobody handles, which cancels the submit silently.
            const response = await examAssessment.submitModelingAssessment();
            expect(response.status()).toBe(200);
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('60%');
        });

        test('Complaints about modeling exercises assessment', async ({ examAssessment, page, studentAssessment, examManagement, courseAssessment, exerciseAssessment }) => {
            await handleComplaint(course, exam, true, ExerciseType.MODELING, page, studentAssessment, examManagement, examAssessment, courseAssessment, exerciseAssessment, false);
        });

        test.afterAll('Delete exam', async ({ browser }) => {
            const page = await newBrowserPage(browser);
            await Commands.login(page, admin);
            await new ExamAPIRequests(page).deleteExam(exam);
            await page.close();
        });
    });

    test.describe.serial('Text exercise assessment', { tag: '@slow' }, () => {
        let exam: Exam;
        let examEnd: Dayjs;

        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(30, 'seconds');
            const page = await newBrowserPage(browser);
            exam = await prepareExam(course, examEnd, ExerciseType.TEXT, page, 2);
        });

        test('Assess a text exercise submission', async ({ page, login, examManagement, examAssessment, examParticipation, courseAssessment, exerciseAssessment }) => {
            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            await waitForExamEnd(exam, page);
            await login(tutor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
            await examAssessment.addNewFeedback(7, 'Good job');
            const response = await examAssessment.submitTextAssessment();
            expect(response.status()).toBe(200);
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('70%');
        });

        test('Instructor makes a second round of assessment', async ({ login, examManagement, examAssessment, examParticipation, courseAssessment, exerciseAssessment }) => {
            await login(instructor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment, true, true);
            await examAssessment.fillFeedback(9, 'Great job');
            const response = await examAssessment.submitTextAssessment();
            expect(response.status()).toBe(200);
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('90%');
        });

        test('Complaints about text exercises assessment', async ({ examAssessment, page, studentAssessment, examManagement, courseAssessment, exerciseAssessment }) => {
            await handleComplaint(course, exam, true, ExerciseType.TEXT, page, studentAssessment, examManagement, examAssessment, courseAssessment, exerciseAssessment, false);
        });

        test.afterAll('Delete exam', async ({ browser }) => {
            const page = await newBrowserPage(browser);
            await Commands.login(page, admin);
            await new ExamAPIRequests(page).deleteExam(exam);
            await page.close();
        });
    });

    test.describe.serial('File upload exercise assessment', { tag: '@slow' }, () => {
        let exam: Exam;
        let examEnd: Dayjs;

        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(40, 'seconds');
            const page = await newBrowserPage(browser);
            exam = await prepareExam(course, examEnd, ExerciseType.FILE_UPLOAD, page, 2);
        });

        test('Assess a file upload exercise submission', async ({
            page,
            login,
            examManagement,
            fileUploadExerciseAssessment,
            examParticipation,
            courseAssessment,
            exerciseAssessment,
        }) => {
            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            await waitForExamEnd(exam, page);
            await login(tutor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
            await fileUploadExerciseAssessment.addNewFeedback(7, 'Good job');
            await fileUploadExerciseAssessment.submitFeedback();
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('70%');
        });

        test('Instructor makes a second round of assessment', async ({
            login,
            examManagement,
            fileUploadExerciseAssessment,
            examParticipation,
            courseAssessment,
            exerciseAssessment,
        }) => {
            await login(instructor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment, true, true);
            // The second round starts from a copy of the first one, so the feedback the tutor left is already there and
            // gets a new value instead of being added again.
            await fileUploadExerciseAssessment.fillFeedback(9, 'Better than it looks');
            await fileUploadExerciseAssessment.submitFeedback();
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('90%');
        });

        test.afterAll('Delete exam', async ({ browser }) => {
            const page = await newBrowserPage(browser);
            await Commands.login(page, admin);
            await new ExamAPIRequests(page).deleteExam(exam);
            await page.close();
        });
    });

    test.describe('Quiz exercise assessment', { tag: '@slow' }, () => {
        let exam: Exam;
        let examEnd: Dayjs;
        let resultDate: Dayjs;

        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(30, 'seconds');
            resultDate = examEnd.add(5, 'seconds');
            const page = await newBrowserPage(browser);
            exam = await prepareExam(course, examEnd, ExerciseType.QUIZ, page);
        });

        test('Assesses quiz automatically', async ({ page, login, examManagement, courseAssessment, examParticipation }) => {
            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            // Wait for exam end + grace period (10s) so the evaluate button is enabled on load.
            // The button's disabled state is computed once during component init and not re-evaluated.
            const graceEnd = examEnd.add(10, 'seconds');
            if (dayjs().isBefore(graceEnd)) {
                await page.waitForTimeout(graceEnd.diff(dayjs(), 'ms') + 5000);
            }
            await page.goto(`/course-management/${course.id}/exams/${exam.id}/assessment-dashboard`);
            await page.waitForLoadState('domcontentloaded');
            const response = await courseAssessment.clickEvaluateQuizzes();
            expect(response.status()).toBe(200);
            if (dayjs().isBefore(resultDate)) {
                await page.waitForTimeout(resultDate.diff(dayjs(), 'ms') + 3000);
            }
            await examManagement.checkQuizSubmission(course.id!, exam.id!, studentOneName, '[5 / 10 Points] 50%');
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('50%');
        });

        test.afterAll('Delete exam', async ({ browser }) => {
            const page = await newBrowserPage(browser);
            await Commands.login(page, admin);
            await new ExamAPIRequests(page).deleteExam(exam);
            await page.close();
        });
    });
});

test.describe.serial('Exam assessment dashboard and scores across two correction rounds', { tag: '@slow' }, () => {
    // Both rounds are assessed here, so the block needs room for the exam to end plus two assessments.
    test.describe.configure({ timeout: 240_000 });

    const dashboardCourse = { id: SEED_COURSES.examAssessment.id } as any;
    let exam: Exam;
    let examEnd: Dayjs;
    let exerciseId: number;

    test.beforeAll('Prepare exam', async ({ browser }) => {
        examEnd = dayjs().add(40, 'seconds');
        const page = await newBrowserPage(browser);
        exam = await prepareExam(dashboardCourse, examEnd, ExerciseType.TEXT, page, 2);
        // prepareExam leaves the page signed in as the student who took the exam, and reading the exercise groups needs
        // staff rights.
        await Commands.login(page, admin);
        const exerciseGroups = await new ExamAPIRequests(page).getExerciseGroups(exam);
        exerciseId = exerciseGroups.flatMap((group) => group.exercises ?? [])[0].id!;
    });

    test('Dashboard offers only the first round until the second correction is enabled', async ({ page, login, examManagement, courseAssessment, exerciseAssessment }) => {
        await login(instructor);
        await examManagement.verifySubmitted(dashboardCourse.id!, exam.id!, studentOneName);
        await waitForExamEnd(exam, page);

        await login(tutor);
        await examManagement.openAssessmentDashboard(dashboardCourse.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT);
        await courseAssessment.clickExerciseDashboardButton(0, EXAM_DASHBOARD_TIMEOUT);
        await exerciseAssessment.clickHaveReadInstructionsButton();

        // Both headings are always rendered for a two-round exam; what the second correction gates is the submissions
        // table below them, which is replaced by an explanatory message until an instructor enables the round.
        await expect(page.getByRole('heading', { name: /Correction Round: 1/ })).toBeVisible();
        await expect(page.getByRole('heading', { name: /Correction Round: 2/ })).toBeVisible();
        await expect(page.getByText('This correction round is not yet enabled.')).toHaveCount(1);
        // Nothing is assessed yet, so the first round has a submission on offer and no assessed row.
        await expect(page.locator('[data-testid="start-new-assessment"]')).toHaveCount(1);
        await expect(page.locator('[data-testid="open-assessment"]')).toHaveCount(0);
    });

    test('First round assessment shows up on the dashboard and opens the second round', async ({
        page,
        login,
        examAssessment,
        courseAssessment,
        examManagement,
        exerciseAssessment,
    }) => {
        await login(tutor);
        await startAssessing(dashboardCourse.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment, false, false);
        await examAssessment.addNewFeedback(6, 'First corrector');
        const response = await examAssessment.submitTextAssessment();
        expect(response.status()).toBe(200);

        // Back on the dashboard the assessed submission is listed for round 1 with the score the tutor gave.
        await examManagement.openAssessmentDashboard(dashboardCourse.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT);
        await courseAssessment.clickExerciseDashboardButton(0, EXAM_DASHBOARD_TIMEOUT);
        await expect(page.locator('[data-testid="open-assessment"]')).toHaveCount(1);
        await expect(page.getByText('60%').first()).toBeVisible();
        // The first round has nothing left to hand out, and the second round is still not enabled.
        await expect(page.locator('[data-testid="start-new-assessment"]')).toHaveCount(0);
        await expect(page.getByText('This correction round is not yet enabled.')).toHaveCount(1);
    });

    test('Second round is assessed independently and both rounds keep their own result', async ({
        page,
        login,
        examAssessment,
        examParticipation,
        courseAssessment,
        examManagement,
        exerciseAssessment,
    }) => {
        await login(instructor);
        await examManagement.openAssessmentDashboard(dashboardCourse.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT);
        await courseAssessment.clickExerciseDashboardButton(0, EXAM_DASHBOARD_TIMEOUT);
        await exerciseAssessment.toggleSecondCorrectionRound();
        // The round sections only render for someone who has confirmed the instructions, so that comes before asserting.
        await exerciseAssessment.clickHaveReadInstructionsButton();

        // Enabling the second correction replaces the message with the round's own submissions table, and it offers the
        // submission the tutor already assessed.
        await expect(page.getByText('This correction round is not yet enabled.')).toHaveCount(0);
        await expect(page.getByRole('heading', { name: /Correction Round: 2/ })).toBeVisible();
        await expect(page.locator('[data-testid="start-new-assessment"]')).toHaveCount(1);

        await exerciseAssessment.clickStartNewAssessment();
        await examAssessment.fillFeedback(9, 'Second corrector');
        const response = await examAssessment.submitTextAssessment();
        expect(response.status()).toBe(200);

        // The dashboard lists what the signed-in corrector assessed, so the instructor sees their second round only,
        // with the score they gave.
        await examManagement.openAssessmentDashboard(dashboardCourse.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT);
        await courseAssessment.clickExerciseDashboardButton(0, EXAM_DASHBOARD_TIMEOUT);
        await expect(page.getByRole('heading', { name: /Correction Round: 2/ })).toBeVisible();
        await expect(page.locator('[data-testid="open-assessment"]')).toHaveCount(1);
        await expect(page.getByText('90%').first()).toBeVisible();

        // The tutor still sees their own first round with the score they gave, so the second round did not overwrite it.
        await login(tutor);
        await examManagement.openAssessmentDashboard(dashboardCourse.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT);
        await courseAssessment.clickExerciseDashboardButton(0, EXAM_DASHBOARD_TIMEOUT);
        await expect(page.locator('[data-testid="open-assessment"]')).toHaveCount(1);
        await expect(page.getByText('60%').first()).toBeVisible();
        // The student is shown the second corrector's result, not the first one.
        await login(studentOne, `/courses/${dashboardCourse.id}/exams/${exam.id}`);
        await examParticipation.checkResultScore('90%');
    });

    test('Exercise scores page offers the actions of each correction round', async ({ page, login }) => {
        await login(instructor);
        // The scores page renders one set of assessment actions per correction round of the exam.
        await page.goto(`/course-management/${dashboardCourse.id}/text-exercises/${exerciseId}/scores`);
        await page.waitForLoadState('domcontentloaded');

        const studentRow = page.locator('tr', { hasText: studentOneName });
        await expect(studentRow).toBeVisible({ timeout: EXAM_DASHBOARD_TIMEOUT });

        // Both rounds are assessed, so each round offers to open its own assessment rather than to start one. Getting
        // the round wrong here is what indexing the results by the round used to do.
        await expect(studentRow.getByRole('link', { name: 'Open assessment of correction round 1' })).toBeVisible();
        await expect(studentRow.getByRole('link', { name: 'Open assessment of correction round 2' })).toBeVisible();
        await expect(studentRow.getByRole('link', { name: 'Assess submission in correction round 1' })).toHaveCount(0);
        await expect(studentRow.getByRole('link', { name: 'Assess submission in correction round 2' })).toHaveCount(0);
        // Both assessments are finished, so neither round is cancellable.
        await expect(studentRow.getByRole('button', { name: /Cancel assessment of correction round/ })).toHaveCount(0);
        // The score column shows the final result, which is the second corrector's.
        await expect(studentRow.getByText('90%')).toBeVisible();
    });

    test('Exam scores page reports both correction rounds', async ({ page, login, examManagement, examAPIRequests }) => {
        await login(instructor);
        await page.goto(`/course-management/${dashboardCourse.id}/exams/${exam.id}`);
        await page.waitForLoadState('domcontentloaded');
        await examManagement.openScoresPage();
        await page.waitForURL(`**/exams/${exam.id}/scores`);
        await page.waitForLoadState('domcontentloaded');

        // The two extra column groups only render once a second correction has been started.
        await expect(page.getByText('First correction', { exact: true })).toBeVisible();
        await expect(page.getByText('Second correction', { exact: true })).toBeVisible();

        const scores = await examAPIRequests.getExamScores(exam);
        expect(scores.hasSecondCorrectionAndStarted).toBe(true);
        const studentResult = scores.studentResults.find((result: any) => result.login === studentOne.username);
        expect(studentResult).toBeDefined();
        // The overall result is the second corrector's, so the second round reached the scores page and the first one
        // did not overwrite it.
        expect(studentResult.overallPointsAchieved).toBe(9);
        expect(studentResult.overallScoreAchieved).toBe(90);
        // The first corrector gave 6 of 10 points, which the dashboard test above shows as 60%. The two rounds are
        // reported next to each other rather than one replacing the other.
        expect(studentResult.overallPointsAchievedInFirstCorrection).toBe(6);

        // The student row shows the final points, not the first corrector's.
        const studentRow = page.locator('tr', { hasText: studentOne.username });
        await expect(studentRow).toBeVisible();
        await expect(studentRow.getByText('9', { exact: true }).first()).toBeVisible();
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        await Commands.login(page, admin);
        await new ExamAPIRequests(page).deleteExam(exam);
        await page.close();
    });
});

test.describe.serial('Cancelling one correction round leaves the other one alone', { tag: '@slow' }, () => {
    test.describe.configure({ timeout: 240_000 });

    const cancelCourse = { id: SEED_COURSES.examAssessment.id } as any;
    let exam: Exam;
    let examEnd: Dayjs;
    let exerciseId: number;

    test.beforeAll('Prepare exam', async ({ browser }) => {
        examEnd = dayjs().add(40, 'seconds');
        const page = await newBrowserPage(browser);
        exam = await prepareExam(cancelCourse, examEnd, ExerciseType.TEXT, page, 2);
        await Commands.login(page, admin);
        const exerciseGroups = await new ExamAPIRequests(page).getExerciseGroups(exam);
        exerciseId = exerciseGroups.flatMap((group) => group.exercises ?? [])[0].id!;
    });

    test('First round is assessed and the second one is left as a draft', async ({ page, login, examAssessment, examManagement, courseAssessment, exerciseAssessment }) => {
        await login(instructor);
        await examManagement.verifySubmitted(cancelCourse.id!, exam.id!, studentOneName);
        await waitForExamEnd(exam, page);

        // The tutor finishes the first round.
        await login(tutor);
        await startAssessing(cancelCourse.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
        await examAssessment.addNewFeedback(6, 'First corrector');
        const response = await examAssessment.submitTextAssessment();
        expect(response.status()).toBe(200);

        // The instructor starts the second round but does not submit it, which is what leaves a draft behind.
        await login(instructor);
        await startAssessing(cancelCourse.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment, true, true);
        await expect(exerciseAssessment.getLockedMessage()).toBeVisible();
    });

    test('Scores view offers to continue and to cancel only the round that is a draft', async ({ page, login }) => {
        await login(instructor);
        await page.goto(`/course-management/${cancelCourse.id}/text-exercises/${exerciseId}/scores`);
        await page.waitForLoadState('domcontentloaded');

        const studentRow = page.locator('tr', { hasText: studentOneName });
        await expect(studentRow).toBeVisible({ timeout: EXAM_DASHBOARD_TIMEOUT });

        // The finished first round can be opened and cannot be cancelled.
        await expect(studentRow.getByRole('link', { name: 'Open assessment of correction round 1' })).toBeVisible();
        await expect(studentRow.getByRole('button', { name: 'Cancel assessment of correction round 1' })).toHaveCount(0);
        // The draft second round can be continued and cancelled.
        await expect(studentRow.getByRole('link', { name: 'Continue assessment in correction round 2' })).toBeVisible();
        await expect(studentRow.getByRole('button', { name: 'Cancel assessment of correction round 2' })).toBeVisible();
        // The draft has no score yet, so the column still shows the first corrector's result.
        await expect(studentRow.getByText('60%')).toBeVisible();
    });

    test('Cancelling the second round releases it and keeps the first round assessed', async ({ page, login }) => {
        await login(instructor);
        await page.goto(`/course-management/${cancelCourse.id}/text-exercises/${exerciseId}/scores`);
        await page.waitForLoadState('domcontentloaded');

        const studentRow = page.locator('tr', { hasText: studentOneName });
        await expect(studentRow).toBeVisible({ timeout: EXAM_DASHBOARD_TIMEOUT });

        // Cancelling asks for confirmation, and a dialog nobody answers is dismissed, which would cancel nothing.
        page.once('dialog', (dialog) => void dialog.accept());
        const cancelResponse = page.waitForResponse((response) => response.url().includes('/cancel-assessment') && response.request().method() === 'POST');
        await studentRow.getByRole('button', { name: 'Cancel assessment of correction round 2' }).click();
        expect((await cancelResponse).status()).toBeLessThan(400);

        // The second round is on offer again, and nothing about the first round changed. Releasing the wrong round is
        // what made cancelling round 1 release round 2 before (#13396).
        await expect(studentRow.getByRole('link', { name: 'Assess submission in correction round 2' })).toBeVisible({ timeout: EXAM_DASHBOARD_TIMEOUT });
        await expect(studentRow.getByRole('button', { name: /Cancel assessment of correction round/ })).toHaveCount(0);
        await expect(studentRow.getByRole('link', { name: 'Open assessment of correction round 1' })).toBeVisible();
        await expect(studentRow.getByText('60%')).toBeVisible();
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        await Commands.login(page, admin);
        await new ExamAPIRequests(page).deleteExam(exam);
        await page.close();
    });
});

test.describe.serial('A test run of an exam with two correction rounds', { tag: '@slow' }, () => {
    test.describe.configure({ timeout: 180_000 });

    const testRunCourse = { id: SEED_COURSES.examAssessment.id } as any;
    let exam: Exam;
    let exerciseId: number;

    test.beforeAll('Prepare exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        exam = await prepareExam(testRunCourse, dayjs().add(40, 'seconds'), ExerciseType.TEXT, page, 2);
        await Commands.login(page, admin);
        const exerciseGroups = await new ExamAPIRequests(page).getExerciseGroups(exam);
        exerciseId = exerciseGroups.flatMap((group) => group.exercises ?? [])[0].id!;
    });

    test('Does not offer a second correction round', async ({ page, login }) => {
        await login(instructor);
        // The dashboard of a test run lives on its own route, which is what tells the page it is a test run.
        await page.goto(`/course-management/${testRunCourse.id}/exams/${exam.id}/test-assessment-dashboard/${exerciseId}`);
        await page.waitForLoadState('domcontentloaded');

        // A test run is a dry run for the instructor, so a second corrector never enters the picture: the toggle that
        // enables the round is not offered, and no round hands out submissions.
        await expect(page.getByTestId('toggle-second-correction')).toHaveCount(0);
        await expect(page.locator('[data-testid="start-new-assessment"]')).toHaveCount(0);
        await expect(page.getByText('This correction round is not yet enabled.')).toHaveCount(0);
        // The regular dashboard of the same exercise does offer the toggle, so the difference is the test run itself.
        await page.goto(`/course-management/${testRunCourse.id}/exams/${exam.id}/assessment-dashboard/${exerciseId}`);
        await page.waitForLoadState('domcontentloaded');
        await expect(page.getByTestId('toggle-second-correction')).toHaveCount(1);
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        await Commands.login(page, admin);
        await new ExamAPIRequests(page).deleteExam(exam);
        await page.close();
    });
});

test.describe('Exam grading', { tag: '@slow' }, () => {
    test.describe.serial('Instructor sets grades and student receives a grade', () => {
        let exam: Exam;
        let examEnd: Dayjs;

        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(30, 'seconds');
            const page = await newBrowserPage(browser);
            exam = await prepareExam(course, examEnd, ExerciseType.TEXT, page);
        });

        test('Set exam gradings', async ({ login, page, examManagement, examGrading }) => {
            await login(instructor);
            await page.goto(`/course-management/${course.id}/exams/${exam.id}`);
            await page.waitForLoadState('domcontentloaded');
            await examManagement.openGradingKey();
            await examGrading.addGradeStep(40, '5.0');
            await examGrading.addGradeStep(15, '4.0');
            await examGrading.addGradeStep(15, '3.0');
            await examGrading.addGradeStep(15, '2.0');
            await examGrading.enterLastGradeName('1.0');
            await examGrading.selectFirstPassingGrade('4.0');
            await examGrading.saveGradingKey();
            await page.locator('button[deletequestion="artemisApp.gradingSystem.deleteQuestion"]').waitFor({ state: 'visible' });
        });

        test('Check student grade', async ({ page, login, examManagement, examAssessment, examParticipation, courseAssessment, exerciseAssessment }) => {
            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            await waitForExamEnd(exam, page);
            await login(tutor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
            await examAssessment.addNewFeedback(7, 'Good job');
            const response = await examAssessment.submitTextAssessment();
            expect(response.status()).toBe(200);
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('70%');
            await examParticipation.verifyGradingKeyOnFinalPage('2.0');
        });

        test.afterAll('Delete exam', async ({ browser }) => {
            const page = await newBrowserPage(browser);
            await Commands.login(page, admin);
            await new ExamAPIRequests(page).deleteExam(exam);
            await page.close();
        });
    });
});

test.describe('Exam statistics', { tag: '@slow' }, () => {
    // This test creates an exam, has 4 students participate, waits for the exam to end,
    // assesses all submissions, and then checks statistics — all within the test timeout.
    // A generous timeout is needed because the exam must end before assessment can begin;
    // on multi-node CI the worst-case run hovers around 280s, so we budget 360s.
    test.describe.configure({ timeout: 360_000 });

    let exam: Exam;
    let exercise: Exercise;
    let examEnd: Dayjs;
    const students = [studentOne, studentTwo, studentThree, studentFour];

    test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
        await login(admin);
        // 180s window (was 60s): the 'Participate in exam' beforeEach below has 4 students
        // each go through startParticipation + open exercise + submit + handInEarly. Under
        // multi-node CI load this routinely takes >60s, by which time the exam has ended and
        // the conduction page redirects, leaving the navigation bar's exercise group title
        // missing. 180s leaves comfortable headroom for the 4-student sequential loop while
        // still letting `waitForExamEnd` return promptly once everyone has handed in.
        examEnd = dayjs().add(180, 'seconds');
        const examConfig = {
            course,
            title: 'exam' + generateUUID(),
            visibleDate: dayjs().subtract(3, 'minutes'),
            startDate: dayjs().subtract(2, 'minutes'),
            endDate: examEnd,
            examMaxPoints: 10,
            numberOfExercisesInExam: 1,
            // no grace period: assessment only opens after the exam end plus the grace period, and this spec's
            // budget is already tight without waiting out createExam's 30s default
            gracePeriod: 0,
        };
        exam = await examAPIRequests.createExam(examConfig);
        const textFixture = 'loremIpsum.txt';
        exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.registerStudentForExam(exam, studentTwo);
        await examAPIRequests.registerStudentForExam(exam, studentThree);
        await examAPIRequests.registerStudentForExam(exam, studentFour);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
    });

    test.beforeEach('Set exam grading', async ({ examAPIRequests, login }) => {
        await login(instructor);
        await examAPIRequests.setExamGradingScale(exam, examStatisticsSample.gradingScale);
    });

    test.beforeEach('Participate in exam', async ({ examParticipation, examNavigation }) => {
        for (const student of students) {
            await examParticipation.startParticipation(student, course, exam);
            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
            await examParticipation.handInEarly();
        }
    });

    test.beforeEach('Assess a text exercise submission', async ({ login, page, examManagement, examAssessment, courseAssessment, exerciseAssessment }) => {
        await login(tutor);
        await waitForExamEnd(exam, page);
        await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);

        const assessment = examStatisticsSample.assessment;
        for (let i = 0; i < students.length; i++) {
            await examAssessment.addNewFeedback(assessment[i].points, assessment[i].feedback);
            const response = await examAssessment.submitTextAssessment();
            expect(response.status()).toBe(200);
            if (i < students.length - 1) {
                await examAssessment.nextAssessment();
            }
        }
    });

    test('Check exam statistics', async ({ login, page, examManagement, examAPIRequests }) => {
        await login(instructor);
        await page.goto(`/course-management/${course.id}/exams/${exam.id}`);
        await page.waitForLoadState('domcontentloaded');
        await examManagement.openScoresPage();
        await page.waitForURL(`**/exams/${exam.id}/scores`);
        await page.waitForLoadState('domcontentloaded');
        const examScores = new ExamScoresPage(page);
        await examScores.checkExamStatistics(examStatisticsSample.statistics);
        await examScores.checkGradeDistributionChart();
        const scores = await examAPIRequests.getExamScores(exam);
        await examScores.checkStudentResults(scores.studentResults);
    });

    test.afterEach('Delete exam', async ({ examAPIRequests }) => {
        await examAPIRequests.deleteExam(exam);
    });
});

async function handleComplaint(
    course: Course,
    exam: Exam,
    reject: boolean,
    exerciseType: ExerciseType,
    page: Page,
    studentAssessment: StudentAssessmentPage,
    examManagement: ExamManagementPage,
    examAssessment: ExamAssessmentPage,
    courseAssessment: CourseAssessmentDashboardPage,
    exerciseAssessment: ExerciseAssessmentDashboardPage,
    isFirstTimeAssessing: boolean = true,
) {
    const complaintText = 'Lorem ipsum dolor sit amet';
    const complaintResponseText = ' consetetur sadipscing elitr';

    await Commands.login(page, studentOne, `/courses/${course.id}/exams/${exam.id}`);
    await studentAssessment.startComplaint();
    await studentAssessment.enterComplaint(complaintText);
    await studentAssessment.submitComplaint();
    await examAssessment.checkComplaintMessage('Your complaint has been submitted');

    await Commands.login(page, instructor, `/course-management/${course.id}/exams`);
    await examManagement.openAssessmentDashboard(course.id!, exam.id!);
    await courseAssessment.clickExerciseDashboardButton();
    if (isFirstTimeAssessing) {
        await exerciseAssessment.clickHaveReadInstructionsButton();
    }
    await exerciseAssessment.clickEvaluateComplaint();
    await exerciseAssessment.checkComplaintText(complaintText);
    page.on('dialog', (dialog) => dialog.accept());
    if (reject) {
        await examAssessment.rejectComplaint(complaintResponseText, true, exerciseType);
    } else {
        await examAssessment.acceptComplaint(complaintResponseText, true, exerciseType);
    }
    if (exerciseType == ExerciseType.MODELING || reject) {
        await examAssessment.checkComplaintMessage('Response to complaint has been submitted');
    } else {
        await examAssessment.checkComplaintMessage('The assessment was updated successfully.');
    }

    await Commands.login(page, studentOne, `/courses/${course.id}/exams/${exam.id}`);
    if (reject) {
        await studentAssessment.checkComplaintStatusText('Complaint was rejected');
    } else {
        await studentAssessment.checkComplaintStatusText('Complaint was accepted');
    }
    await studentAssessment.checkComplaintResponseText(complaintResponseText);
}
