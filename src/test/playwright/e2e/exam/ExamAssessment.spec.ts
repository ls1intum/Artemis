import dayjs, { Dayjs } from 'dayjs';
import { Exercise, ExerciseType } from '../../support/constants';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor, users } from '../../support/users';
import { Page, expect } from '@playwright/test';

import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { ExamManagementPage } from '../../support/pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from '../../support/pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from '../../support/pageobjects/assessment/ExerciseAssessmentDashboardPage';
import { StudentAssessmentPage } from '../../support/pageobjects/assessment/StudentAssessmentPage';
import { ExamAssessmentPage } from '../../support/pageobjects/assessment/ExamAssessmentPage';
import { test } from '../../support/fixtures';
import { CourseManagementAPIRequests } from '../../support/requests/CourseManagementAPIRequests';
import { generateUUID, newBrowserPage, prepareExam, startAssessing, waitForExamEnd } from '../../support/utils';
import { EXAM_DASHBOARD_TIMEOUT } from '../../support/timeouts';
import examStatisticsSample from '../../fixtures/exam/statistics.json';
import { ExamScoresPage } from '../../support/pageobjects/exam/ExamScoresPage';

let exam: Exam;

let course: Course;
let examEnd: Dayjs;
let studentOneName: string;

test.beforeAll('Create course', async ({ browser }) => {
    const page = await newBrowserPage(browser);
    const courseManagementAPIRequests = new CourseManagementAPIRequests(page);

    await Commands.login(page, admin);
    course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
    await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
    await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
    await courseManagementAPIRequests.addStudentToCourse(course, studentThree);
    await courseManagementAPIRequests.addStudentToCourse(course, studentFour);
    await courseManagementAPIRequests.addTutorToCourse(course, tutor);
    await courseManagementAPIRequests.addInstructorToCourse(course, instructor);

    studentOneName = (await users.getUserInfo(studentOne.username, page)).name!;
});

test.describe('Exam assessment', () => {
    test.describe.configure({ mode: 'serial' });

    test.describe.serial('Programming exercise assessment', { tag: '@sequential' }, () => {
        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(2, 'minutes');
            const page = await newBrowserPage(browser);
            exam = await prepareExam(course, examEnd, ExerciseType.PROGRAMMING, page);
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
            await waitForExamEnd(examEnd, page);
            await login(tutor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
            await examAssessment.addNewFeedback(2, 'Good job');
            await examAssessment.submit();
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('66.2%');
        });

        test('Complaints about programming exercises assessment', async ({ examAssessment, page, studentAssessment, examManagement, courseAssessment, exerciseAssessment }) => {
            await handleComplaint(course, exam, false, ExerciseType.PROGRAMMING, page, studentAssessment, examManagement, examAssessment, courseAssessment, exerciseAssessment);
        });
    });

    test.describe.serial('Modeling exercise assessment', { tag: '@slow' }, () => {
        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(45, 'seconds');
            const page = await newBrowserPage(browser);
            exam = await prepareExam(course, examEnd, ExerciseType.MODELING, page);
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
            await waitForExamEnd(examEnd, page);
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

        test('Complaints about modeling exercises assessment', async ({ examAssessment, page, studentAssessment, examManagement, courseAssessment, exerciseAssessment }) => {
            await handleComplaint(course, exam, true, ExerciseType.MODELING, page, studentAssessment, examManagement, examAssessment, courseAssessment, exerciseAssessment);
        });
    });

    test.describe.serial('Text exercise assessment', { tag: '@slow' }, () => {
        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(20, 'seconds');
            const page = await newBrowserPage(browser);
            exam = await prepareExam(course, examEnd, ExerciseType.TEXT, page, 2);
        });

        test('Assess a text exercise submission', async ({ page, login, examManagement, examAssessment, examParticipation, courseAssessment, exerciseAssessment }) => {
            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
            await waitForExamEnd(examEnd, page);
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
    });

    test.describe('Quiz exercise assessment', { tag: '@slow' }, () => {
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
            if (dayjs().isBefore(examEnd)) {
                await page.waitForTimeout(examEnd.diff(dayjs(), 'ms') + 10000);
            }
            await examManagement.openAssessmentDashboard(course.id!, exam.id!, 60000);
            await page.goto(`/course-management/${course.id}/exams/${exam.id}/assessment-dashboard`);
            const response = await courseAssessment.clickEvaluateQuizzes();
            expect(response.status()).toBe(200);
            if (dayjs().isBefore(resultDate)) {
                await page.waitForTimeout(resultDate.diff(dayjs(), 'ms') + 1000);
            }
            await examManagement.checkQuizSubmission(course.id!, exam.id!, studentOneName, '[5 / 10 Points] 50%');
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('50%');
        });
    });
});

test.describe('Exam grading', { tag: '@slow' }, () => {
    test.describe.serial('Instructor sets grades and student receives a grade', () => {
        let exam: Exam;

        test.beforeAll('Prepare exam', async ({ browser }) => {
            examEnd = dayjs().add(40, 'seconds');
            const page = await newBrowserPage(browser);
            exam = await prepareExam(course, examEnd, ExerciseType.TEXT, page);
        });

        test('Set exam gradings', async ({ login, page, examManagement, examGrading }) => {
            await login(instructor);
            await page.goto(`course-management/${course.id}/exams/${exam.id}`);
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
            await waitForExamEnd(examEnd, page);
            await login(tutor);
            await startAssessing(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT, examManagement, courseAssessment, exerciseAssessment);
            await examAssessment.addNewFeedback(7, 'Good job');
            const response = await examAssessment.submitTextAssessment();
            expect(response.status()).toBe(200);
            await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
            await examParticipation.checkResultScore('70%');
            await examParticipation.verifyGradingKeyOnFinalPage('2.0');
        });
    });
});

test.describe('Exam statistics', { tag: '@sequential' }, () => {
    let exercise: Exercise;
    const students = [studentOne, studentTwo, studentThree, studentFour];

    test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
        await login(admin);
        const examConfig = {
            course,
            title: 'exam' + generateUUID(),
            visibleDate: dayjs().subtract(3, 'minutes'),
            startDate: dayjs().subtract(2, 'minutes'),
            endDate: dayjs().add(1, 'minutes'),
            examMaxPoints: 10,
            numberOfExercisesInExam: 1,
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

    test.beforeEach('Assess a text exercise submission', async ({ login, examManagement, examAssessment, courseAssessment, exerciseAssessment }) => {
        await login(tutor);
        await startAssessing(course.id!, exam.id!, 60000, examManagement, courseAssessment, exerciseAssessment);

        const assessment = examStatisticsSample.assessment;
        for (let i = 0; i < students.length; i++) {
            await examAssessment.addNewFeedback(assessment[i].points, assessment[i].feedback);
            const response = await examAssessment.submitTextAssessment();
            expect(response.status()).toBe(200);
            await examAssessment.nextAssessment();
        }
    });

    test('Check exam statistics', async ({ login, page, examManagement, examAPIRequests }) => {
        await login(instructor);
        await page.goto(`course-management/${course.id}/exams/${exam.id}`);
        await examManagement.openScoresPage();
        await page.waitForURL(`**/exams/${exam.id}/scores`);
        await page.waitForLoadState('load');
        const examScores = new ExamScoresPage(page);
        await examScores.checkExamStatistics(examStatisticsSample.statistics);
        await examScores.checkGradeDistributionChart(examStatisticsSample.gradeDistribution);
        const scores = await examAPIRequests.getExamScores(exam);
        await examScores.checkStudentResults(scores.studentResults);
    });
});

test.afterAll('Delete course', async ({ browser }) => {
    const page = await newBrowserPage(browser);
    const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
    await courseManagementAPIRequests.deleteCourse(course, admin);
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
