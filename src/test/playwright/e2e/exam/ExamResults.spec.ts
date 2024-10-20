import { test } from '../../support/fixtures';
import { Exam } from 'app/entities/exam/exam.model';
import { Commands } from '../../support/commands';
import { admin, instructor, studentOne, tutor } from '../../support/users';
import { Course } from 'app/entities/course.model';
import dayjs, { Dayjs } from 'dayjs';
import { generateUUID } from '../../support/utils';
import { Exercise, ExerciseType } from '../../support/constants';
import { ExamManagementPage } from '../../support/pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from '../../support/pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from '../../support/pageobjects/assessment/ExerciseAssessmentDashboardPage';
import javaPartiallySuccessfulSubmission from '../../fixtures/exercise/programming/java/partially_successful/submission.json';
import { CourseManagementAPIRequests } from '../../support/requests/CourseManagementAPIRequests';
import { ProgrammingExerciseTaskStatus } from '../../support/pageobjects/exam/ExamResultsPage';
import { Page } from '@playwright/test';
import { StudentExam } from 'app/entities/student-exam.model';

test.describe('Exam Results', () => {
    let course: Course;

    test.beforeEach('Create course', async ({ browser }) => {
        const page = await browser.newPage();
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);

        await Commands.login(page, admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
    });

    const testCases = [
        { exerciseType: ExerciseType.TEXT, resultScore: '70%' },
        { exerciseType: ExerciseType.PROGRAMMING, resultScore: '46.2%' },
        { exerciseType: ExerciseType.QUIZ, resultScore: '50%' },
        { exerciseType: ExerciseType.MODELING, resultScore: '40%' },
    ];

    test.describe('Check exam exercise results', () => {
        for (const testCase of testCases) {
            let exam: Exam;
            let studentExam: StudentExam;
            let examEndDate: Dayjs;
            let exercise: Exercise;
            const exerciseTypeString = testCase.exerciseType.toString().toLowerCase();

            test.describe(`Check exam results for ${exerciseTypeString} exercise`, () => {
                test.beforeEach('Prepare exam', async ({ login, examAPIRequests }) => {
                    await login(admin);

                    if (testCase.exerciseType === ExerciseType.PROGRAMMING) {
                        examEndDate = dayjs().add(1, 'minutes').add(30, 'seconds');
                    } else {
                        examEndDate = dayjs().add(30, 'seconds');
                    }
                    const examConfig = {
                        course,
                        title: 'exam' + generateUUID(),
                        visibleDate: dayjs().subtract(3, 'minutes'),
                        startDate: dayjs().subtract(2, 'minutes'),
                        endDate: examEndDate,
                        publishResultsDate: examEndDate.add(1, 'seconds'),
                        examMaxPoints: 10,
                        numberOfExercisesInExam: 1,
                    };
                    exam = await examAPIRequests.createExam(examConfig);
                });

                test.beforeEach('Add exercise to exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
                    await login(admin);
                    let additionalData: any;
                    switch (testCase.exerciseType) {
                        case ExerciseType.TEXT:
                            additionalData = { textFixture: 'loremIpsum.txt' };
                            break;
                        case ExerciseType.PROGRAMMING:
                            additionalData = { submission: javaPartiallySuccessfulSubmission };
                            break;
                        case ExerciseType.QUIZ:
                            additionalData = { quizExerciseID: 0 };
                            break;
                    }
                    exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, testCase.exerciseType, additionalData);
                    await examAPIRequests.registerStudentForExam(exam, studentOne);
                    const studentExams = await examAPIRequests.getAllStudentExams(exam);
                    studentExam = studentExams[0];
                });

                test.beforeEach('Participate in exam', async ({ login, examParticipation, examNavigation, examStartEnd }) => {
                    await login(admin);
                    await examParticipation.startParticipation(studentOne, course, exam);
                    await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
                    await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
                    await examParticipation.handInEarly();
                    await examStartEnd.pressShowSummary();
                });

                test.beforeEach(
                    'Assess student submission',
                    async ({ page, login, examManagement, examAssessment, courseAssessment, exerciseAssessment, modelingExerciseAssessment, exerciseAPIRequests }) => {
                        switch (testCase.exerciseType) {
                            case ExerciseType.TEXT:
                                await login(tutor);
                                await startAssessing(course.id!, exam.id!, 0, 60000, examManagement, courseAssessment, exerciseAssessment);
                                await examAssessment.addNewFeedback(7, 'Good job');
                                await examAssessment.submitTextAssessment();
                                break;
                            case ExerciseType.MODELING:
                                await login(tutor);
                                await startAssessing(course.id!, exam.id!, 0, 60000, examManagement, courseAssessment, exerciseAssessment);
                                await modelingExerciseAssessment.addNewFeedback(5, 'Good');
                                await modelingExerciseAssessment.openAssessmentForComponent(0);
                                await modelingExerciseAssessment.assessComponent(-1, 'Wrong');
                                await modelingExerciseAssessment.clickNextAssessment();
                                await modelingExerciseAssessment.assessComponent(0, 'Neutral');
                                await modelingExerciseAssessment.clickNextAssessment();
                                await examAssessment.submitModelingAssessment();
                                break;
                            case ExerciseType.QUIZ:
                                await login(instructor);
                                await waitForExamEnd(examEndDate, page);
                                await exerciseAPIRequests.evaluateExamQuizzes(exam);
                                break;
                        }
                    },
                );

                test(`Check exam ${exerciseTypeString} exercise results`, async ({ page, login, examParticipation, examResultsPage }) => {
                    await login(studentOne);
                    await waitForExamEnd(examEndDate, page);
                    await page.goto(`/courses/${course.id}/exams/${exam.id}`);
                    await examParticipation.checkResultScore(testCase.resultScore, exercise.id!);

                    switch (testCase.exerciseType) {
                        case ExerciseType.TEXT:
                            await examResultsPage.checkTextExerciseContent(exercise.id!, exercise.additionalData!.textFixture!);
                            await examResultsPage.checkAdditionalFeedback(exercise.id!, 7, 'Good job');
                            break;
                        case ExerciseType.PROGRAMMING:
                            await examResultsPage.checkProgrammingExerciseAssessments(exercise.id!, 'Wrong', 7);
                            await examResultsPage.checkProgrammingExerciseAssessments(exercise.id!, 'Correct', 6);
                            const taskStatuses: ProgrammingExerciseTaskStatus[] = [
                                ProgrammingExerciseTaskStatus.SUCCESS,
                                ProgrammingExerciseTaskStatus.SUCCESS,
                                ProgrammingExerciseTaskStatus.SUCCESS,
                                ProgrammingExerciseTaskStatus.FAILURE,
                                ProgrammingExerciseTaskStatus.FAILURE,
                                ProgrammingExerciseTaskStatus.FAILURE,
                                ProgrammingExerciseTaskStatus.FAILURE,
                            ];
                            await examResultsPage.checkProgrammingExerciseTasks(exercise.id!, taskStatuses);
                            break;
                        case ExerciseType.QUIZ:
                            await examResultsPage.checkQuizExerciseScore(exercise.id!, 5, 10);
                            const studentAnswers = [true, false, true, false];
                            const correctAnswers = [true, true, false, false];
                            await examResultsPage.checkQuizExerciseAnswers(exercise.id!, studentAnswers, correctAnswers);
                            break;
                        case ExerciseType.MODELING:
                            await examResultsPage.checkAdditionalFeedback(exercise.id!, 5, 'Good');
                            await examResultsPage.checkModellingExerciseAssessment(exercise.id!, 'class Class', 'Wrong', -1);
                            await examResultsPage.checkModellingExerciseAssessment(exercise.id!, 'abstract class Abstract', 'Neutral', 0);
                            break;
                    }
                });

                if (testCase.exerciseType === ExerciseType.TEXT) {
                    test('Check exam result overview', async ({ page, login, examAPIRequests, examResultsPage }) => {
                        await login(studentOne);
                        await page.goto(`/courses/${course.id}/exams/${exam.id}`);
                        const gradeSummary = await examAPIRequests.getGradeSummary(exam, studentExam);
                        await examResultsPage.checkGradeSummary(gradeSummary);
                    });
                }
            });
        }
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
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

async function waitForExamEnd(examEndDate: dayjs.Dayjs, page: Page) {
    if (examEndDate > dayjs()) {
        const timeToWait = examEndDate.diff(dayjs());
        await page.waitForTimeout(timeToWait);
    }
}
