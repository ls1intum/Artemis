import { test } from '../../support/fixtures';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { admin, instructor, studentOne, tutor } from '../../support/users';
import dayjs, { Dayjs } from 'dayjs';
import { generateUUID } from '../../support/utils';
import { Exercise, ExerciseType, ProgrammingLanguage } from '../../support/constants';
import { ExamAssessmentPage } from '../../support/pageobjects/assessment/ExamAssessmentPage';
import { ModelingExerciseAssessmentEditor } from '../../support/pageobjects/assessment/ModelingExerciseAssessmentEditor';
import { ExamParticipationPage } from '../../support/pageobjects/exam/ExamParticipationPage';
import { ExamNavigationBar } from '../../support/pageobjects/exam/ExamNavigationBar';
import { ExamStartEndPage } from '../../support/pageobjects/exam/ExamStartEndPage';
import { ModelingEditor } from '../../support/pageobjects/exercises/modeling/ModelingEditor';
import { OnlineEditorPage } from '../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { MultipleChoiceQuiz } from '../../support/pageobjects/exercises/quiz/MultipleChoiceQuiz';
import { TextEditorPage } from '../../support/pageobjects/exercises/text/TextEditorPage';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';
import { ExamExerciseGroupCreationPage } from '../../support/pageobjects/exam/ExamExerciseGroupCreationPage';
import cPartiallySuccessfulSubmission from '../../fixtures/exercise/programming/c/partially_successful/submission.json';
import { ProgrammingExerciseTaskStatus } from '../../support/pageobjects/exam/ExamResultsPage';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { SEED_COURSES } from '../../support/seedData';

const course = { id: SEED_COURSES.examResults.id } as any;

// All 4 exercise types share a single exam to avoid redundant lifecycle waits.
// Uses test.describe.serial so that setup (beforeAll) runs once before all tests.
test.describe.serial('Exam Results', { tag: '@slow' }, () => {
    let exam: Exam;
    let studentExam: StudentExam;
    let examEndDate: Dayjs;
    const exercises: Record<string, Exercise> = {};

    test.beforeAll('Create exam with all exercise types', async ({ browser }) => {
        test.setTimeout(300_000); // Creating 4 exercise groups with programming builds
        const context = await browser.newContext({ ignoreHTTPSErrors: true });
        const page = await context.newPage();
        await Commands.login(page, admin);
        const examAPIRequests = new ExamAPIRequests(page);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);
        const examExerciseGroupCreation = new ExamExerciseGroupCreationPage(page, examAPIRequests, exerciseAPIRequests);

        // Allow enough time for 4 exercise groups to be created (including C programming
        // build ~10-20s) AND for the student to submit all 4 exercises in the next beforeAll.
        // The third beforeAll waits for this date + grace period before assessing.
        // Use 3 minutes to provide buffer for slow CI environments with parallel test load.
        examEndDate = dayjs().add(3, 'minutes');
        const examConfig = {
            course,
            title: 'exam' + generateUUID(),
            visibleDate: dayjs().subtract(3, 'minutes'),
            startDate: dayjs().subtract(2, 'minutes'),
            endDate: examEndDate,
            publishResultsDate: examEndDate.add(1, 'seconds'),
            examMaxPoints: 40,
            numberOfExercisesInExam: 4,
            gracePeriod: 10,
        };
        exam = await examAPIRequests.createExam(examConfig);

        // Add all 4 exercise types to the same exam
        exercises['text'] = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture: 'loremIpsum.txt' });
        exercises['programming'] = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, {
            submission: cPartiallySuccessfulSubmission,
            programmingLanguage: ProgrammingLanguage.C,
        });
        exercises['quiz'] = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 });
        exercises['modeling'] = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING);

        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        const studentExams = await examAPIRequests.getAllStudentExams(exam);
        studentExam = studentExams[0];
        await examAPIRequests.prepareExerciseStartForExam(exam);
        await page.close();
    });

    test.beforeAll('Participate in exam', async ({ browser }) => {
        test.setTimeout(300_000); // Programming exercise submission waits for build result
        const context = await browser.newContext({ ignoreHTTPSErrors: true });
        const page = await context.newPage();
        await Commands.login(page, admin);
        const examNavigation = new ExamNavigationBar(page);
        const examStartEnd = new ExamStartEndPage(page);
        const examParticipation = new ExamParticipationPage(
            examNavigation,
            examStartEnd,
            new ModelingEditor(page),
            new OnlineEditorPage(page),
            new MultipleChoiceQuiz(page),
            new TextEditorPage(page),
            page,
        );

        await examParticipation.startParticipation(studentOne, course, exam);

        // Submit all 4 exercises
        const exerciseEntries = Object.entries(exercises);
        for (let i = 0; i < exerciseEntries.length; i++) {
            const [, exercise] = exerciseEntries[i];
            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
        }
        // Save the last exercise before handing in (navigating away triggers auto-save).
        // Wait briefly to ensure the modeling editor has fully processed the drag operations.
        await page.waitForTimeout(2000);
        await examNavigation.openOrSaveExerciseByTitle(exerciseEntries[0][1].exerciseGroup!.title!);
        // Wait for auto-save to complete
        await page.waitForTimeout(3000);
        await examParticipation.handInEarly();
        await examStartEnd.pressShowSummary();
        await page.close();
    });

    test.beforeAll('Assess all submissions', async ({ browser }) => {
        test.setTimeout(300_000); // Assessment involves multiple dashboard loads with retries
        const context = await browser.newContext({ ignoreHTTPSErrors: true });
        const page = await context.newPage();
        // Wait for exam end + grace period (10s) so submissions are available for assessment.
        // Add extra buffer (5s) to account for clock drift and server processing time.
        const graceEnd = examEndDate.add(10, 'seconds');
        if (dayjs().isBefore(graceEnd)) {
            const timeToWait = graceEnd.diff(dayjs(), 'ms') + 5000;
            await page.waitForTimeout(timeToWait);
        }

        const examAssessment = new ExamAssessmentPage(page);
        const modelingExerciseAssessment = new ModelingExerciseAssessmentEditor(page);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);

        // Only text and modeling exercises need manual assessment (quiz is auto-evaluated, programming is auto-assessed).
        // Navigate directly to each exercise's assessment dashboard by exercise ID.
        await Commands.login(page, tutor);

        // Assess text exercise
        await navigateToExerciseAssessment(page, course.id!, exam.id!, exercises['text'].id!);
        await examAssessment.addNewFeedback(7, 'Good job');
        await examAssessment.submitTextAssessment();

        // Assess modeling exercise
        await navigateToExerciseAssessment(page, course.id!, exam.id!, exercises['modeling'].id!);

        await modelingExerciseAssessment.addNewFeedback(5, 'Good');
        await modelingExerciseAssessment.openAssessmentForComponent(0);
        await modelingExerciseAssessment.assessComponent(-1, 'Wrong');
        await modelingExerciseAssessment.clickNextAssessment();
        await modelingExerciseAssessment.assessComponent(0, 'Neutral');
        await modelingExerciseAssessment.clickNextAssessment();
        await examAssessment.submitModelingAssessment();

        // Evaluate quiz exercise
        await Commands.login(page, instructor);
        await exerciseAPIRequests.evaluateExamQuizzes(exam);

        // Programming exercise is auto-assessed
        await page.close();
    });

    test('Check text exercise results', async ({ page, login, examParticipation, examResultsPage }) => {
        const exercise = exercises['text'];
        await login(studentOne);
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        await page.waitForLoadState('networkidle');
        await examParticipation.checkResultScore('70%', exercise.id!);
        await examResultsPage.checkTextExerciseContent(exercise.id!, exercise.additionalData!.textFixture!);
        await examResultsPage.checkAdditionalFeedback(exercise.id!, 7, 'Good job');
    });

    test('Check programming exercise results', async ({ page, login, examParticipation, examResultsPage }) => {
        const exercise = exercises['programming'];
        await login(studentOne);
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        await page.waitForLoadState('networkidle');
        await examParticipation.checkResultScore('50%', exercise.id!);
        await examResultsPage.checkProgrammingExerciseAssessments(exercise.id!, 'Wrong', 4);
        await examResultsPage.checkProgrammingExerciseAssessments(exercise.id!, 'Correct', 4);
        const taskStatuses: ProgrammingExerciseTaskStatus[] = [
            ProgrammingExerciseTaskStatus.SUCCESS, // Compile (TestCompile)
            ProgrammingExerciseTaskStatus.FAILURE, // Output (TestOutput, TestOutputASan, TestOutputUBSan, TestOutputLSan)
            ProgrammingExerciseTaskStatus.NOT_EXECUTED, // Sanitizers (TestASan, TestUBSan, TestLSan)
        ];
        await examResultsPage.checkProgrammingExerciseTasks(exercise.id!, taskStatuses);
    });

    test('Check quiz exercise results', async ({ page, login, examParticipation, examResultsPage }) => {
        const exercise = exercises['quiz'];
        await login(studentOne);
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        await page.waitForLoadState('networkidle');
        await examParticipation.checkResultScore('50%', exercise.id!);
        await examResultsPage.checkQuizExerciseScore(exercise.id!, 5, 10);
        const studentAnswers = [true, false, true, false];
        const correctAnswers = [true, true, false, false];
        await examResultsPage.checkQuizExerciseAnswers(exercise.id!, studentAnswers, correctAnswers);
    });

    test('Check modeling exercise results', async ({ page, login, examParticipation, examResultsPage }) => {
        const exercise = exercises['modeling'];
        await login(studentOne);
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        await page.waitForLoadState('networkidle');
        await examParticipation.checkResultScore('40%', exercise.id!);
        await examResultsPage.checkAdditionalFeedback(exercise.id!, 5, 'Good');
        await examResultsPage.checkModellingExerciseAssessment(exercise.id!, 'class TestClass', 'Wrong', -1);
        await examResultsPage.checkModellingExerciseAssessment(exercise.id!, 'class TestClass', 'Neutral', 0);
    });

    test('Check exam result overview', async ({ page, login, examAPIRequests, examResultsPage }) => {
        await login(studentOne);
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        await page.waitForLoadState('networkidle');
        const gradeSummary = await examAPIRequests.getGradeSummary(exam, studentExam);
        await examResultsPage.checkGradeSummary(gradeSummary);
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        const context = await browser.newContext({ ignoreHTTPSErrors: true });
        const page = await context.newPage();
        await Commands.login(page, admin);
        const examAPIRequests = new ExamAPIRequests(page);
        await examAPIRequests.deleteExam(exam);
        await page.close();
    });
});

async function navigateToExerciseAssessment(page: import('@playwright/test').Page, courseId: number, examId: number, exerciseId: number) {
    const url = `/course-management/${courseId}/exams/${examId}/assessment-dashboard/${exerciseId}`;
    await page.goto(url);
    await page.waitForLoadState('networkidle');

    // Click "I have read the instructions" to register tutor participation (persisted server-side).
    // After this, reloads will show the submissions table directly.
    const participateButton = page.locator('#participate-in-assessment');
    await Commands.reloadUntilFound(page, participateButton, 10000, 90000);
    await participateButton.click();
    // Wait for the start-assessment button (reloadUntilFound works because participation is persisted)
    const startButton = page.locator('#start-new-assessment').first();
    await Commands.reloadUntilFound(page, startButton, 10000, 90000);
    await startButton.click();
}
