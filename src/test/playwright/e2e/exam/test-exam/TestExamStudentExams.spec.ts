import { Exam } from 'app/exam/shared/entities/exam.model';
import { UserCredentials, admin, studentOne, studentThree, studentTwo, users } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { Exercise, ExerciseType } from '../../../support/constants';
import dayjs from 'dayjs';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { ExamParticipationPage } from '../../../support/pageobjects/exam/ExamParticipationPage';
import { ExamNavigationBar } from '../../../support/pageobjects/exam/ExamNavigationBar';
import { SEED_COURSES } from '../../../support/seedData';

// Common primitives
const uid = generateUUID();
const examTitle = 'test-exam' + uid;
const textFixture = 'loremIpsum-short.txt';
const studentNames = new Map<UserCredentials, string>();

let examExercise: Exercise;

const course = { id: SEED_COURSES.testExam.id } as any;

test.describe('Test Exam - student exams', { tag: '@slow' }, () => {
    let exam: Exam;

    test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation, examParticipation, examNavigation }) => {
        await login(admin);

        const examConfig = {
            course,
            title: examTitle,
            testExam: true,
            startDate: dayjs().subtract(1, 'day'),
            visibleDate: dayjs().subtract(2, 'days'),
            workingTime: 120,
            examMaxPoints: 10,
            numberOfCorrectionRoundsInExam: 1,
        };

        exam = await examAPIRequests.createExam(examConfig);
        examExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });

        await participateInExam(studentOne, course, exam, true, true, examParticipation, examNavigation);
        await participateInExam(studentTwo, course, exam, true, false, examParticipation, examNavigation);
        await participateInExam(studentThree, course, exam, false, false, examParticipation, examNavigation);
    });

    test.beforeEach('Get student names', async ({ login, page }) => {
        await login(admin);

        const studentOneInfo = await users.getUserInfo(studentOne.username, page);
        const studentTwoInfo = await users.getUserInfo(studentTwo.username, page);
        const studentThreeInfo = await users.getUserInfo(studentThree.username, page);

        studentNames.set(studentOne, studentOneInfo.name!);
        studentNames.set(studentTwo, studentTwoInfo.name!);
        studentNames.set(studentThree, studentThreeInfo.name!);
    });

    test.describe('Check exam participants and their submissions', () => {
        test('Open the list of exam students', async ({ page, studentExamManagement }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/student-exams`);

            await studentExamManagement.checkExamStudent(studentOne.username);
            await studentExamManagement.checkExamStudent(studentTwo.username);
            await studentExamManagement.checkExamStudent(studentThree.username);

            await expect(studentExamManagement.getStudentExamRows()).toHaveCount(3);

            await studentExamManagement.checkStudentExamProperty(studentOne.username, 'Started', 'Yes');
            await studentExamManagement.checkStudentExamProperty(studentTwo.username, 'Started', 'Yes');
            await studentExamManagement.checkStudentExamProperty(studentThree.username, 'Started', 'No');

            await studentExamManagement.checkStudentExamProperty(studentOne.username, 'Submitted', 'Yes');
            await studentExamManagement.checkStudentExamProperty(studentTwo.username, 'Submitted', 'No');
            await studentExamManagement.checkStudentExamProperty(studentThree.username, 'Submitted', 'No');

            await studentExamManagement.checkStudentExamProperty(studentTwo.username, 'Used working time', '0s');
        });

        test('Search for a student in exams', async ({ page, studentExamManagement }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/student-exams`);
            // Wait for the data table to load before searching
            await page.locator('.data-table-container').waitFor({ state: 'visible' });
            await studentExamManagement.getStudentExamRows().first().waitFor({ state: 'visible' });

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
        course: any,
        exam: Exam,
        toStart: boolean,
        toSubmit: boolean,
        examParticipation: ExamParticipationPage,
        examNavigation: ExamNavigationBar,
    ) {
        if (!toStart) {
            await examParticipation.openExam(student, course, exam);
            await examParticipation.almostStartExam();
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

    test.afterEach('Delete exam', async ({ examAPIRequests }) => {
        await examAPIRequests.deleteExam(exam);
    });
});
