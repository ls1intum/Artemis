import { test } from '../../support/fixtures';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { admin, instructor, studentOne, tutor } from '../../support/users';
import { Course } from 'app/core/course/shared/entities/course.model';
import dayjs, { Dayjs } from 'dayjs';
import { generateUUID, waitForExamEnd } from '../../support/utils';
import { EXAM_DASHBOARD_TIMEOUT } from '../../support/timeouts';
import { Exercise, ExerciseType, ProgrammingLanguage } from '../../support/constants';
import { ExamManagementPage } from '../../support/pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from '../../support/pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from '../../support/pageobjects/assessment/ExerciseAssessmentDashboardPage';
import { ExamAssessmentPage } from '../../support/pageobjects/assessment/ExamAssessmentPage';
import { ModelingExerciseAssessmentEditor } from '../../support/pageobjects/assessment/ModelingExerciseAssessmentEditor';
import { ExamParticipationPage } from '../../support/pageobjects/exam/ExamParticipationPage';
import { ExamNavigationBar } from '../../support/pageobjects/exam/ExamNavigationBar';
import { ExamStartEndPage } from '../../support/pageobjects/exam/ExamStartEndPage';
import { CoursesPage } from '../../support/pageobjects/course/CoursesPage';
import { CourseOverviewPage } from '../../support/pageobjects/course/CourseOverviewPage';
import { ModelingEditor } from '../../support/pageobjects/exercises/modeling/ModelingEditor';
import { OnlineEditorPage } from '../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { MultipleChoiceQuiz } from '../../support/pageobjects/exercises/quiz/MultipleChoiceQuiz';
import { TextEditorPage } from '../../support/pageobjects/exercises/text/TextEditorPage';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';
import { ExamExerciseGroupCreationPage } from '../../support/pageobjects/exam/ExamExerciseGroupCreationPage';
import cPartiallySuccessfulSubmission from '../../fixtures/exercise/programming/c/partially_successful/submission.json';
import { CourseManagementAPIRequests } from '../../support/requests/CourseManagementAPIRequests';
import { ProgrammingExerciseTaskStatus } from '../../support/pageobjects/exam/ExamResultsPage';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';

// All 4 exercise types share a single exam to avoid redundant lifecycle waits.
// Uses test.describe.serial so that setup (beforeAll) runs once before all tests.
test.describe.serial('Exam Results', { tag: '@slow' }, () => {
    let course: Course;
    let exam: Exam;
    let studentExam: StudentExam;
    let examEndDate: Dayjs;
    const exercises: Record<string, Exercise> = {};

    test.beforeAll('Create course', async ({ browser }) => {
        const page = await browser.newPage();
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
        await Commands.login(page, admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        await page.close();
    });

    test.beforeAll('Create exam with all exercise types', async ({ browser }) => {
        const page = await browser.newPage();
        await Commands.login(page, admin);
        const examAPIRequests = new ExamAPIRequests(page);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);
        const examExerciseGroupCreation = new ExamExerciseGroupCreationPage(page, exerciseAPIRequests, examAPIRequests);

        // Programming exercises need the most time for CI builds
        examEndDate = dayjs().add(45, 'seconds');
        const examConfig = {
            course,
            title: 'exam' + generateUUID(),
            visibleDate: dayjs().subtract(3, 'minutes'),
            startDate: dayjs().subtract(2, 'minutes'),
            endDate: examEndDate,
            publishResultsDate: examEndDate.add(1, 'seconds'),
            examMaxPoints: 40,
            numberOfExercisesInExam: 4,
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
        const studentExams = await examAPIRequests.getAllStudentExams(exam);
        studentExam = studentExams[0];
        await examAPIRequests.prepareExerciseStartForExam(exam);
        await page.close();
    });

    test.beforeAll('Participate in exam', async ({ browser }) => {
        const page = await browser.newPage();
        await Commands.login(page, admin);
        const examNavigation = new ExamNavigationBar(page);
        const examStartEnd = new ExamStartEndPage(page);
        const examParticipation = new ExamParticipationPage(
            new CoursesPage(page),
            new CourseOverviewPage(page),
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
        for (const [, exercise] of Object.entries(exercises)) {
            await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
        }

        await examParticipation.handInEarly();
        await examStartEnd.pressShowSummary();
        await page.close();
    });

    test.beforeAll('Assess all submissions', async ({ browser }) => {
        const page = await browser.newPage();
        await waitForExamEnd(examEndDate, page);

        const examManagement = new ExamManagementPage(page);
        const courseAssessment = new CourseAssessmentDashboardPage(page);
        const exerciseAssessment = new ExerciseAssessmentDashboardPage(page);
        const examAssessment = new ExamAssessmentPage(page);
        const modelingExerciseAssessment = new ModelingExerciseAssessmentEditor(page);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);

        // Assess text exercise (index 0)
        await Commands.login(page, tutor);
        await startAssessing(course.id!, exam.id!, 0, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
        await examAssessment.addNewFeedback(7, 'Good job');
        await examAssessment.submitTextAssessment();

        // Assess modeling exercise (index 3)
        await startAssessing(course.id!, exam.id!, 3, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
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
        await examParticipation.checkResultScore('70%', exercise.id!);
        await examResultsPage.checkTextExerciseContent(exercise.id!, exercise.additionalData!.textFixture!);
        await examResultsPage.checkAdditionalFeedback(exercise.id!, 7, 'Good job');
    });

    test('Check programming exercise results', async ({ page, login, examParticipation, examResultsPage }) => {
        const exercise = exercises['programming'];
        await login(studentOne);
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
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
        await examParticipation.checkResultScore('40%', exercise.id!);
        await examResultsPage.checkAdditionalFeedback(exercise.id!, 5, 'Good');
        await examResultsPage.checkModellingExerciseAssessment(exercise.id!, 'class Class', 'Wrong', -1);
        await examResultsPage.checkModellingExerciseAssessment(exercise.id!, 'abstract class Abstract', 'Neutral', 0);
    });

    test('Check exam result overview', async ({ page, login, examAPIRequests, examResultsPage }) => {
        await login(studentOne);
        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
        const gradeSummary = await examAPIRequests.getGradeSummary(exam, studentExam);
        await examResultsPage.checkGradeSummary(gradeSummary);
    });

    test.afterAll('Delete course', async ({ browser }) => {
        const page = await browser.newPage();
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
        await Commands.login(page, admin);
        await courseManagementAPIRequests.deleteCourse(course, admin);
        await page.close();
    });
});

async function startAssessing(
    courseID: number,
    examID: number,
    exerciseIndex: number = 0,
    timeout: number,
    examManagement: ExamManagementPage,
    courseAssessment: CourseAssessmentDashboardPage,
    exerciseAssessment: ExerciseAssessmentDashboardPage,
) {
    await examManagement.openAssessmentDashboard(courseID, examID, timeout);
    await courseAssessment.clickExerciseDashboardButton(exerciseIndex);
    await exerciseAssessment.clickHaveReadInstructionsButton();
    await exerciseAssessment.clickStartNewAssessment();
    exerciseAssessment.getLockedMessage();
}
