import { test } from '../../support/fixtures';
import { Exam } from 'app/entities/exam.model';
import { Commands } from '../../support/commands';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor } from '../../support/users';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs';
import { generateUUID } from '../../support/utils';
import { Exercise, ExerciseType } from '../../support/constants';
import { startAssessing } from './ExamAssessment.spec';
import { ExamManagementPage } from '../../support/pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from '../../support/pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from '../../support/pageobjects/assessment/ExerciseAssessmentDashboardPage';
import { ExamAssessmentPage } from '../../support/pageobjects/assessment/ExamAssessmentPage';
import javaPartiallySuccessfulSubmission from '../../fixtures/exercise/programming/java/partially_successful/submission.json';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExamExerciseGroupCreationPage } from '../../support/pageobjects/exam/ExamExerciseGroupCreationPage';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';
import { ExamParticipation } from '../../support/pageobjects/exam/ExamParticipation';
import { CourseManagementAPIRequests } from '../../support/requests/CourseManagementAPIRequests';
import { ExamNavigationBar } from '../../support/pageobjects/exam/ExamNavigationBar';
import { CourseOverviewPage } from '../../support/pageobjects/course/CourseOverviewPage';
import { ExamStartEndPage } from '../../support/pageobjects/exam/ExamStartEndPage';
import { CoursesPage } from '../../support/pageobjects/course/CoursesPage';
import { ModelingEditor } from '../../support/pageobjects/exercises/modeling/ModelingEditor';
import { OnlineEditorPage } from '../../support/pageobjects/exercises/programming/OnlineEditorPage';
import { MultipleChoiceQuiz } from '../../support/pageobjects/exercises/quiz/MultipleChoiceQuiz';
import { TextEditorPage } from '../../support/pageobjects/exercises/text/TextEditorPage';
import { ModelingExerciseAssessmentEditor } from '../../support/pageobjects/assessment/ModelingExerciseAssessmentEditor';
import { ProgrammingExerciseTaskStatus } from '../../support/pageobjects/exam/ExamResultsPage';

test.describe('Exam Results', () => {
    let course: Course;

    test.beforeAll('Create course', async ({ browser }) => {
        const page = await browser.newPage();
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);

        await Commands.login(page, admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
        await courseManagementAPIRequests.addStudentToCourse(course, studentThree);
        await courseManagementAPIRequests.addStudentToCourse(course, studentFour);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
    });

    test.describe.serial('Check exam exercise results', () => {
        let exam: Exam;
        let exerciseArray: Array<Exercise> = [];

        // test.beforeAll('Prepare exam and assess a student submission', async ({ browser }) => {
        //     const examEnd = dayjs().add(40, 'seconds');
        //     const page = await newBrowserPage(browser);
        //     const examAssessment = new ExamAssessmentPage(page);
        //     await prepareExam(course, examEnd, ExerciseType.TEXT, page);
        //
        //     await Commands.login(page, tutor);
        //     await startAssessing(course.id!, exam.id!, 60000, new ExamManagementPage(page), new CourseAssessmentDashboardPage(page), new ExerciseAssessmentDashboardPage(page));
        //     await examAssessment.addNewFeedback(7, 'Good job');
        //     const response = await examAssessment.submitTextAssessment();
        //     expect(response.status()).toBe(200);
        //
        //     await Commands.login(page, studentOne);
        //     await page.goto(`courses/${course.id}/exams/${exam.id}`);
        // });

        test.beforeAll('Prepare exam and assess a student submission', async ({ browser }) => {
            const page = await browser.newPage();
            const examAPIRequests = new ExamAPIRequests(page);
            const exerciseAPIRequests = new ExerciseAPIRequests(page);
            const examExerciseGroupCreation = new ExamExerciseGroupCreationPage(page, examAPIRequests, exerciseAPIRequests);

            await Commands.login(page, admin);
            const endDate = dayjs().add(1, 'minutes').add(30, 'seconds');
            const examConfig = {
                course,
                title: 'exam' + generateUUID(),
                visibleDate: dayjs().subtract(3, 'minutes'),
                startDate: dayjs().subtract(2, 'minutes'),
                endDate: endDate,
                publishResultsDate: endDate.add(1, 'seconds'),
                examMaxPoints: 40,
                numberOfExercisesInExam: 4,
            };
            exam = await examAPIRequests.createExam(examConfig);
            const textExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture: 'loremIpsum.txt' });
            const programmingExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { submission: javaPartiallySuccessfulSubmission });
            const quizExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 });
            const modelingExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING);
            exerciseArray = [textExercise, programmingExercise, quizExercise, modelingExercise];

            await examAPIRequests.registerStudentForExam(exam, studentOne);
            await examAPIRequests.generateMissingIndividualExams(exam);
            await examAPIRequests.prepareExerciseStartForExam(exam);
            const courseList = new CoursesPage(page);
            const courseOverview = new CourseOverviewPage(page);

            const examParticipation = new ExamParticipation(
                courseList,
                courseOverview,
                new ExamNavigationBar(page),
                new ExamStartEndPage(page),
                new ModelingEditor(page),
                new OnlineEditorPage(page, courseList, courseOverview),
                new MultipleChoiceQuiz(page),
                new TextEditorPage(page),
                page,
            );

            // for (let j = 0; j < exerciseArray.length; j++) {
            //     const exercise = exerciseArray[j];
            //     if (exercise.type === ExerciseType.TEXT) {
            //         await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
            //         await page.waitForTimeout(20000);
            //         const submission = await Fixtures.get('loremIpsum-short.txt');
            //         await exerciseAPIRequests.makeTextExerciseSubmission(exercise.id!, submission!, true);
            //         await page.waitForTimeout(20000);
            //         await exerciseAPIRequests.makeTextExerciseSubmission(exercise.id!, submission!, true);
            //     }
            // }

            await examParticipation.startParticipation(studentOne, course, exam);

            const examNavigation = new ExamNavigationBar(page);
            const examStartEnd = new ExamStartEndPage(page);

            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openExerciseAtIndex(j);
                await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
            }

            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();

            const examManagement = new ExamManagementPage(page);
            const examAssessment = new ExamAssessmentPage(page);
            const courseAssessment = new CourseAssessmentDashboardPage(page);
            const exerciseAssessment = new ExerciseAssessmentDashboardPage(page);
            await Commands.login(page, tutor);
            await startAssessing(course.id!, exam.id!, 0, 60000, examManagement, courseAssessment, exerciseAssessment);
            await examAssessment.addNewFeedback(7, 'Good job');
            await examAssessment.submitTextAssessment();

            await startAssessing(course.id!, exam.id!, 1, 60000, examManagement, courseAssessment, exerciseAssessment);

            const modelingExerciseAssessment = new ModelingExerciseAssessmentEditor(page);
            await modelingExerciseAssessment.addNewFeedback(5, 'Good');
            await modelingExerciseAssessment.openAssessmentForComponent(0);
            await modelingExerciseAssessment.assessComponent(-1, 'Wrong');
            await modelingExerciseAssessment.clickNextAssessment();
            await modelingExerciseAssessment.assessComponent(0, 'Neutral');
            await modelingExerciseAssessment.clickNextAssessment();
            await examAssessment.submitModelingAssessment();
        });

        test('Check exam text exercise results', async ({ page, login, examParticipation, examResultsPage }) => {
            await login(studentOne);
            await page.goto(`/courses/${course.id}/exams/${exam.id}`);
            const exercise = exerciseArray[0];
            await examParticipation.checkResultScore('70%', exercise.id!);
            await examResultsPage.checkTextExerciseContent(exercise.id!, exercise.additionalData!.textFixture!);
            await examResultsPage.checkAdditionalFeedback(exercise.id!, 7, 'Good job');
        });

        test.skip('Check exam programming exercise results', async ({ page, login, examParticipation, examResultsPage }) => {
            await login(studentOne);
            await page.goto(`/courses/${course.id}/exams/${exam.id}`);
            const exercise = exerciseArray[1];
            await examParticipation.checkResultScore('46.2%', exercise.id!);
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
        });

        test('Check exam modelling exercise results', async ({ page, login, examParticipation, examResultsPage }) => {
            await login(studentOne);
            await page.goto(`/courses/${course.id}/exams/${exam.id}`);
            const exercise = exerciseArray[3];
            await examParticipation.checkResultScore('40%', exercise.id!);
            await examResultsPage.checkAdditionalFeedback(exercise.id!, 5, 'Good');
            await examResultsPage.checkModellingExerciseAssessment(exercise.id!, 'class Class', 'Wrong', -1);
            await examResultsPage.checkModellingExerciseAssessment(exercise.id!, 'abstract class Abstract', 'Neutral', 0);
        });
    });

    test.afterAll('Delete course', async ({ browser }) => {
        const page = await browser.newPage();
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
