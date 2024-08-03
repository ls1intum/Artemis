import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { UserCredentials, admin, studentOne, studentTwo, users } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { Exercise, ExerciseType } from '../../../support/constants';
import dayjs from 'dayjs';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { ExamParticipationPage } from '../../../support/pageobjects/exam/ExamParticipationPage';
import { ExamNavigationBar } from '../../../support/pageobjects/exam/ExamNavigationBar';

// Common primitives
const uid = generateUUID();
const examTitle = 'test-exam' + uid;
const textFixture = 'loremIpsum-short.txt';
const studentNames = new Map<UserCredentials, string>();

let examExercise: Exercise;

test.describe('Test Exam - student exams', () => {
    test.describe.configure({
        mode: 'default',
        timeout: 180000,
    });

    let course: Course;
    let exam: Exam;

    test.beforeEach('Create course and exam', async ({ login, courseManagementAPIRequests, examAPIRequests, examExerciseGroupCreation, examParticipation, examNavigation }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();

        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);

        const examConfig = {
            course,
            title: examTitle,
            testExam: true,
            startDate: dayjs().subtract(1, 'day'),
            visibleDate: dayjs().subtract(2, 'days'),
            workingTime: 5,
            examMaxPoints: 10,
            numberOfCorrectionRoundsInExam: 1,
        };

        exam = await examAPIRequests.createExam(examConfig);
        examExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });

        await participateInExam(studentOne, course, exam, true, true, examParticipation, examNavigation);
        await participateInExam(studentTwo, course, exam, true, false, examParticipation, examNavigation);
    });

    test.beforeEach('Get student names', async ({ login, page }) => {
        await login(admin);

        const studentOneInfo = await users.getUserInfo(studentOne.username, page);
        const studentTwoInfo = await users.getUserInfo(studentTwo.username, page);

        studentNames.set(studentOne, studentOneInfo.name!);
        studentNames.set(studentTwo, studentTwoInfo.name!);
    });

    test.describe('Check exam participants and their submissions', () => {
        test('Open the list of exam students', async ({ page, navigationBar, courseManagement, examManagement, studentExamManagement }) => {
            await page.goto('/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExamsOfCourse(course.id!);
            await examManagement.openStudentExams(exam.id!);

            await studentExamManagement.checkExamStudent(studentOne.username);
            await studentExamManagement.checkExamStudent(studentTwo.username);

            await expect(studentExamManagement.getStudentExamRows()).toHaveCount(2);

            await studentExamManagement.checkStudentExamProperty(studentOne.username, 'Started', 'Yes');
            await studentExamManagement.checkStudentExamProperty(studentTwo.username, 'Started', 'Yes');

            await studentExamManagement.checkStudentExamProperty(studentOne.username, 'Submitted', 'Yes');
            await studentExamManagement.checkStudentExamProperty(studentTwo.username, 'Submitted', 'No');

            await studentExamManagement.checkStudentExamProperty(studentTwo.username, 'Used working time', '0s');
        });

        test('Search for a student in exams', async ({ page, navigationBar, courseManagement, examManagement, studentExamManagement }) => {
            await page.goto('/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExamsOfCourse(course.id!);
            await examManagement.openStudentExams(exam.id!);

            let searchText = studentOne.username + ', ' + studentTwo.username;
            await studentExamManagement.typeSearchText(searchText);
            await studentExamManagement.checkExamStudent(studentOne.username);
            await studentExamManagement.checkExamStudent(studentTwo.username);

            searchText = studentNames.get(studentOne)! + ', ' + studentNames.get(studentTwo)!;
            await studentExamManagement.typeSearchText(searchText);
            await studentExamManagement.checkExamStudent(studentOne.username);
            await studentExamManagement.checkExamStudent(studentTwo.username);

            searchText = 'Artemis Test User';
            await studentExamManagement.typeSearchText(searchText);
            await studentExamManagement.checkExamStudent(studentOne.username);
            await studentExamManagement.checkExamStudent(studentTwo.username);
        });
    });

    async function participateInExam(
        student: UserCredentials,
        course: Course,
        exam: Exam,
        toStart: boolean,
        toSubmit: boolean,
        examParticipation: ExamParticipationPage,
        examNavigation: ExamNavigationBar,
    ) {
        if (!toStart) {
            await examParticipation.openExam(student, course, exam);
        } else {
            await examParticipation.openExam(student, course, exam);
            await examParticipation.startExam();
            await examNavigation.openOverview();

            await examNavigation.openOrSaveExerciseByTitle(examExercise.exerciseGroup!.title!);
            await examParticipation.makeSubmission(examExercise.id!, examExercise.type!, examExercise.additionalData);
        }

        if (toSubmit) {
            await examParticipation.handInEarly();
        }
    }

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
