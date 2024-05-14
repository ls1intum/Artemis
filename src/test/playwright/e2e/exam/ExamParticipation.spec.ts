import { test } from '../../support/fixtures';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from '../../support/constants';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor, users } from '../../support/users';
import { generateUUID } from '../../support/utils';
import javaAllSuccessfulSubmission from '../../fixtures/exercise/programming/java/all_successful/submission.json';
import dayjs from 'dayjs';
import { Exam } from 'app/entities/exam.model';
import { expect } from '@playwright/test';
import { ExamStartEndPage } from '../../support/pageobjects/exam/ExamStartEndPage';
import { ExamManagementPage } from '../../support/pageobjects/exam/ExamManagementPage';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';

// Common primitives
const textFixture = 'loremIpsum.txt';
const textFixtureShort = 'loremIpsum-short.txt';

test.describe('Exam participation', () => {
    let course: Course;
    let exerciseArray: Array<Exercise> = [];
    let studentTwoName: string;
    let studentThreeName: string;
    let studentFourName: string;

    test.beforeEach('Create course', async ({ login, page, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
        await courseManagementAPIRequests.addStudentToCourse(course, studentThree);
        await courseManagementAPIRequests.addStudentToCourse(course, studentFour);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);

        const studentTwoInfo = await users.getUserInfo(studentTwo.username, page);
        studentTwoName = studentTwoInfo.name!;

        const studentThreeInfo = await users.getUserInfo(studentThree.username, page);
        studentThreeName = studentThreeInfo.name!;

        const studentFourInfo = await users.getUserInfo(studentFour.username, page);
        studentFourName = studentFourInfo.name!;
    });

    test.describe('Early Hand-in', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
            await login(admin);
            exam = await createExam(course, examAPIRequests, { title: examTitle, examMaxPoints: 40, numberOfExercisesInExam: 4 });
            const textExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
            const programmingExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { submission: javaAllSuccessfulSubmission });
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
            await examParticipation.startParticipation(studentTwo, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openExerciseAtIndex(j);
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

        test('Using save and continue to navigate within exam', async ({ login, examParticipation, examNavigation, examManagement }) => {
            await examParticipation.startParticipation(studentThree, course, exam);
            await examNavigation.openExerciseAtIndex(0);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type == ExerciseType.PROGRAMMING) {
                    await examNavigation.openExerciseAtIndex(j + 1);
                } else {
                    await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
                    await examParticipation.clickSaveAndContinue();
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
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type != ExerciseType.PROGRAMMING) {
                    await examNavigation.openExerciseOverview();
                    await examParticipation.selectExerciseOnOverview(j + 1);
                    await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
                }
            }
            await examParticipation.handInEarly();

            await login(instructor);
            await examManagement.verifySubmitted(course.id!, exam.id!, studentFourName);
        });
    });

    test.describe('Early hand-in with continue and reload page', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
            exerciseArray = [];

            await login(admin);
            exam = await createExam(course, examAPIRequests, { title: examTitle });
            await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }).then((response) => {
                exerciseArray.push(response);
            });

            await examAPIRequests.registerStudentForExam(exam, studentTwo);
            await examAPIRequests.registerStudentForExam(exam, studentThree);
            await examAPIRequests.registerStudentForExam(exam, studentFour);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
        });

        test('Participates in the exam, hand-in early, but instead continues', async ({
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
            await examNavigation.openExerciseAtIndex(textExerciseIndex);
            await examParticipation.makeTextExerciseSubmission(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.clickSaveAndContinue();
            await examNavigation.handInEarly();

            await examStartEnd.clickContinue();
            await examNavigation.openExerciseAtIndex(textExerciseIndex);
            await textExerciseEditor.clearSubmission(textExercise.id!);
            await examParticipation.makeTextExerciseSubmission(textExercise.id!, textFixtureShort);
            await examParticipation.clickSaveAndContinue();

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
            await examNavigation.openExerciseAtIndex(textExerciseIndex);
            await examParticipation.makeTextExerciseSubmission(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.clickSaveAndContinue();

            await page.reload();
            await examParticipation.startParticipation(studentThree, course, exam);
            await examNavigation.openExerciseAtIndex(textExerciseIndex);
            await textExerciseEditor.checkCurrentContent(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.clickSaveAndContinue();
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
            await examNavigation.openExerciseAtIndex(textExerciseIndex);
            await examParticipation.makeTextExerciseSubmission(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.clickSaveAndContinue();
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
    });

    test.describe('Normal Hand-in', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
            exerciseArray = [];

            await login(admin);
            exam = await createExam(course, examAPIRequests, { title: examTitle, endDate: dayjs().add(30, 'seconds') });
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
            await examNavigation.openExerciseAtIndex(textExerciseIndex);
            await examParticipation.makeSubmission(textExercise.id!, textExercise.type!, textExercise.additionalData);
            await examParticipation.clickSaveAndContinue();
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
    });

    test.describe('Exam announcements', () => {
        let exam: Exam;
        const students = [studentOne, studentTwo];

        test.beforeEach('Create exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
            await login(admin);
            exam = await createExam(course, examAPIRequests);
            const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
            exerciseArray.push(exercise);
            for (const student of students) {
                await examAPIRequests.registerStudentForExam(exam, student);
            }

            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
        });

        test('Instructor sends an announcement and all participants receive it', async ({ browser, login, navigationBar, courseManagement, examManagement }) => {
            await login(instructor);
            await navigationBar.openCourseManagement();
            await courseManagement.openExamsOfCourse(course.id!);
            await examManagement.openExam(exam.id!);

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

            const announcement = 'Important announcement!';
            await examManagement.openAnnouncementDialog();
            const announcementTypingTime = dayjs();
            await examManagement.typeAnnouncementMessage(announcement);
            await examManagement.verifyAnnouncementContent(announcementTypingTime, announcement, instructor.username);
            await examManagement.sendAnnouncement();

            for (const studentPage of studentPages) {
                const examManagement = new ExamManagementPage(studentPage);
                await examManagement.verifyAnnouncementContent(announcementTypingTime, announcement, instructor.username);
            }
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
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
