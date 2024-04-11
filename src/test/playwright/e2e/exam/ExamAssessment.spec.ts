import dayjs, { Dayjs } from 'dayjs';
import { Exercise, ExerciseType, ProgrammingExerciseAssessmentType } from '../../support/constants';
import { admin, instructor, studentOne, tutor, users } from '../../support/users';
import { Page, expect } from '@playwright/test';

import javaPartiallySuccessful from '../../fixtures/exercise/programming/java/partially_successful/submission.json';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExamExerciseGroupCreationPage } from '../../support/pageobjects/exam/ExamExerciseGroupCreationPage';
import { ExamParticipation } from '../../support/pageobjects/exam/ExamParticipation';
import { ExamNavigationBar } from '../../support/pageobjects/exam/ExamNavigationBar';
import { ExamStartEndPage } from '../../support/pageobjects/exam/ExamStartEndPage';
import { ExamManagementPage } from '../../support/pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from '../../support/pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from '../../support/pageobjects/assessment/ExerciseAssessmentDashboardPage';
import { StudentAssessmentPage } from '../../support/pageobjects/assessment/StudentAssessmentPage';
import { ExamAssessmentPage } from '../../support/pageobjects/assessment/ExamAssessmentPage';
import { test } from '../../support/fixtures';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';
import { CoursesPage } from '../../support/pageobjects/course/CoursesPage';
import { CourseOverviewPage } from '../../support/pageobjects/course/CourseOverviewPage';
import { ModelingEditor } from '../../support/pageobjects/exercises/modeling/ModelingEditor';
import { OnlineEditorPage } from '../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { MultipleChoiceQuiz } from '../../support/pageobjects/exercises/quiz/MultipleChoiceQuiz';
import { TextEditorPage } from '../../support/pageobjects/exercises/text/TextEditorPage';
import { CourseManagementAPIRequests } from '../../support/requests/CourseManagementAPIRequests';
import { newBrowserPage } from '../../support/utils';

let exam: Exam;

test.describe('Exam assessment', () => {
    let course: Course;
    let examEnd: Dayjs;
    let programmingAssessmentSuccessful = false;
    let modelingAssessmentSuccessful = false;
    let textAssessmentSuccessful = false;
    let studentOneName: string;

    test.beforeAll('Create course', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);

        await Commands.login(page, admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        const userInfo = await users.getUserInfo(studentOne.username, page);
        studentOneName = userInfo.name!;
    });

    test.describe.serial('Programming exercise assessment', () => {
        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(2, 'minutes');
            const page = await newBrowserPage(browser);
            await prepareExam(course, examEnd, ExerciseType.PROGRAMMING, page);
        });

        test('Assess a programming exercise submission (MANUAL)', async ({ login, examManagement, examAssessment, examParticipation, courseAssessment, exerciseAssessment }) => {
            test.slow();
            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            await login(tutor);
            await startAssessing(course.id!, exam.id!, 155000, examManagement, courseAssessment, exerciseAssessment);
            await examAssessment.addNewFeedback(2, 'Good job');
            await examAssessment.submit();
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('66.2%');
            programmingAssessmentSuccessful = true;
        });

        test('Complaints about programming exercises assessment', async ({ examAssessment, page, studentAssessment, examManagement, courseAssessment, exerciseAssessment }) => {
            if (programmingAssessmentSuccessful) {
                await handleComplaint(course, exam, false, ExerciseType.PROGRAMMING, page, studentAssessment, examManagement, examAssessment, courseAssessment, exerciseAssessment);
            }
        });
    });

    test.describe.serial('Modeling exercise assessment', () => {
        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(45, 'seconds');
            const page = await newBrowserPage(browser);
            await prepareExam(course, examEnd, ExerciseType.MODELING, page);
        });

        test('Assess a modeling exercise submission', async ({
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
            await login(tutor);
            await startAssessing(course.id!, exam.id!, 60000, examManagement, courseAssessment, exerciseAssessment);
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
            modelingAssessmentSuccessful = true;
        });

        test('Complaints about modeling exercises assessment', async ({ examAssessment, page, studentAssessment, examManagement, courseAssessment, exerciseAssessment }) => {
            if (modelingAssessmentSuccessful) {
                await handleComplaint(course, exam, true, ExerciseType.MODELING, page, studentAssessment, examManagement, examAssessment, courseAssessment, exerciseAssessment);
            }
        });
    });

    test.describe.serial('Text exercise assessment', () => {
        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(40, 'seconds');
            const page = await newBrowserPage(browser);
            await prepareExam(course, examEnd, ExerciseType.TEXT, page);
        });

        test('Assess a text exercise submission', async ({ login, examManagement, examAssessment, examParticipation, courseAssessment, exerciseAssessment }) => {
            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            await login(tutor);
            await startAssessing(course.id!, exam.id!, 60000, examManagement, courseAssessment, exerciseAssessment);
            await examAssessment.addNewFeedback(7, 'Good job');
            const response = await examAssessment.submitTextAssessment();
            expect(response.status()).toBe(200);
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('70%');
            textAssessmentSuccessful = true;
        });

        test('Complaints about text exercises assessment', async ({ examAssessment, page, studentAssessment, examManagement, courseAssessment, exerciseAssessment }) => {
            if (textAssessmentSuccessful) {
                await handleComplaint(course, exam, false, ExerciseType.TEXT, page, studentAssessment, examManagement, examAssessment, courseAssessment, exerciseAssessment);
            }
        });
    });

    test.describe('Quiz exercise assessment', () => {
        let resultDate: Dayjs;

        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(30, 'seconds');
            resultDate = examEnd.add(5, 'seconds');
            const page = await newBrowserPage(browser);
            await prepareExam(course, examEnd, ExerciseType.QUIZ, page);
        });

        test('Assesses quiz automatically', async ({ page, login, examManagement, courseAssessment, examParticipation }) => {
            test.fixme();
            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            if (dayjs().isBefore(examEnd)) {
                await page.waitForTimeout(examEnd.diff(dayjs(), 'ms') + 1000);
            }
            await examManagement.openAssessmentDashboard(course.id!, exam.id!, 60000);
            await page.goto(`/course-management/${course.id}/exams/${exam.id}/assessment-dashboard`);
            const response = await courseAssessment.clickEvaluateQuizzes();
            expect(response.status()).toBe(200);
            if (dayjs().isBefore(resultDate)) {
                await page.waitForTimeout(resultDate.diff(dayjs(), 'ms') + 1000);
            }
            await examManagement.checkQuizSubmission(course.id!, exam.id!, studentOneName, '50%');
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('50%');
        });
    });

    test.afterAll('Delete course', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});

async function prepareExam(course: Course, end: dayjs.Dayjs, exerciseType: ExerciseType, page: Page) {
    const examAPIRequests = new ExamAPIRequests(page);
    const exerciseAPIRequests = new ExerciseAPIRequests(page);
    const examExerciseGroupCreation = new ExamExerciseGroupCreationPage(page, examAPIRequests, exerciseAPIRequests);
    const courseList = new CoursesPage(page);
    const courseOverview = new CourseOverviewPage(page);
    const modelingExerciseEditor = new ModelingEditor(page);
    const programmingExerciseEditor = new OnlineEditorPage(page, courseList, courseOverview);
    const quizExerciseMultipleChoice = new MultipleChoiceQuiz(page);
    const textExerciseEditor = new TextEditorPage(page);
    const examNavigation = new ExamNavigationBar(page);
    const examStartEnd = new ExamStartEndPage(page);
    const examParticipation = new ExamParticipation(
        courseList,
        courseOverview,
        examNavigation,
        examStartEnd,
        modelingExerciseEditor,
        programmingExerciseEditor,
        quizExerciseMultipleChoice,
        textExerciseEditor,
        page,
    );

    await Commands.login(page, admin);
    const resultDate = end.add(1, 'second');
    const examConfig = {
        course,
        startDate: dayjs(),
        endDate: end,
        numberOfCorrectionRoundsInExam: 1,
        examStudentReviewStart: resultDate,
        examStudentReviewEnd: resultDate.add(1, 'minute'),
        publishResultsDate: resultDate,
        gracePeriod: 10,
    };
    exam = await examAPIRequests.createExam(examConfig);
    await examAPIRequests.registerStudentForExam(exam, studentOne);
    let additionalData = {};
    switch (exerciseType) {
        case ExerciseType.PROGRAMMING:
            additionalData = { submission: javaPartiallySuccessful, progExerciseAssessmentType: ProgrammingExerciseAssessmentType.SEMI_AUTOMATIC };
            break;
        case ExerciseType.TEXT:
            additionalData = { textFixture: 'loremIpsum-short.txt' };
            break;
        case ExerciseType.QUIZ:
            additionalData = { quizExerciseID: 0 };
            break;
    }

    const response = await examExerciseGroupCreation.addGroupWithExercise(exam, exerciseType, additionalData);
    await examAPIRequests.generateMissingIndividualExams(exam);
    await examAPIRequests.prepareExerciseStartForExam(exam);
    response.additionalData = additionalData;
    await makeExamSubmission(course, exam, response, page, examParticipation, examNavigation, examStartEnd);
}

async function makeExamSubmission(
    course: Course,
    exam: Exam,
    exercise: Exercise,
    page: Page,
    examParticipation: ExamParticipation,
    examNavigation: ExamNavigationBar,
    examStartEnd: ExamStartEndPage,
) {
    await examParticipation.startParticipation(studentOne, course, exam);
    await examNavigation.openExerciseAtIndex(0);
    await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
    await page.waitForTimeout(2000);
    await examNavigation.handInEarly();
    await examStartEnd.finishExam();
}

async function startAssessing(
    courseID: number,
    examID: number,
    timeout: number,
    examManagement: ExamManagementPage,
    courseAssessment: CourseAssessmentDashboardPage,
    exerciseAssessment: ExerciseAssessmentDashboardPage,
) {
    await examManagement.openAssessmentDashboard(courseID, examID, timeout);
    await courseAssessment.clickExerciseDashboardButton();
    await exerciseAssessment.clickHaveReadInstructionsButton();
    await exerciseAssessment.clickStartNewAssessment();
    exerciseAssessment.getLockedMessage();
}

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
    await exerciseAssessment.clickHaveReadInstructionsButton();

    await exerciseAssessment.clickEvaluateComplaint();
    await exerciseAssessment.checkComplaintText(complaintText);
    page.on('dialog', (dialog) => dialog.accept());
    if (reject) {
        await examAssessment.rejectComplaint(complaintResponseText, true, exerciseType);
    } else {
        await examAssessment.acceptComplaint(complaintResponseText, true, exerciseType);
    }
    if (exerciseType == ExerciseType.MODELING) {
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
