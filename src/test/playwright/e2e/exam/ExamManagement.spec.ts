import { expect } from '@playwright/test';
import { admin, instructor, studentOne } from '../../support/users';
import { generateUUID, newBrowserPage } from '../../support/utils';
import { test } from '../../support/fixtures';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Commands } from '../../support/commands';
import { CourseManagementAPIRequests } from '../../support/requests/CourseManagementAPIRequests';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';

test.describe('Exam management', () => {
    test.describe('Exercise group', () => {
        let course: Course;
        let exam: Exam;

        test.beforeEach('Create exam', async ({ login, courseManagementAPIRequests, examAPIRequests }) => {
            await login(admin);
            course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            exam = await examAPIRequests.createExam({ course, title: 'Exam ' + generateUUID() });
        });

        test.beforeEach(async ({ login }) => {
            await login(instructor);
        });

        test.describe('Manage Group', () => {
            let exerciseGroup: ExerciseGroup;

            test.beforeEach('Add exercise group for exam', async ({ examAPIRequests }) => {
                exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
            });

            test('Adds a text exercise', async ({ page, examManagement, textExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams`);
                await examManagement.openExerciseGroups(exam.id!);
                await examExerciseGroups.clickAddTextExercise(exerciseGroup.id!);
                const textExerciseTitle = 'Text ' + generateUUID();
                await textExerciseCreation.typeTitle(textExerciseTitle);
                await textExerciseCreation.typeMaxPoints(10);
                const response = await textExerciseCreation.create();
                expect(response.status()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, textExerciseTitle);
            });

            test('Adds a quiz exercise', async ({ page, examManagement, quizExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams`);
                await examManagement.openExerciseGroups(exam.id!);
                await examExerciseGroups.clickAddQuizExercise(exerciseGroup.id!);
                const quizExerciseTitle = 'Quiz ' + generateUUID();
                await quizExerciseCreation.setTitle(quizExerciseTitle);
                await quizExerciseCreation.addMultipleChoiceQuestion(quizExerciseTitle, 10);
                const response = await quizExerciseCreation.saveQuiz();
                expect(response.status()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, quizExerciseTitle);
            });

            test('Adds a modeling exercise', async ({ page, examManagement, modelingExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams`);
                await examManagement.openExerciseGroups(exam.id!);
                await examExerciseGroups.clickAddModelingExercise(exerciseGroup.id!);
                const modelingExerciseTitle = 'Modeling ' + generateUUID();
                await modelingExerciseCreation.setTitle(modelingExerciseTitle);
                await modelingExerciseCreation.setPoints(10);
                const response = await modelingExerciseCreation.save();
                expect(response.status()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, modelingExerciseTitle);
            });

            test('Adds a programming exercise', async ({ page, examManagement, programmingExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams`);
                await examManagement.openExerciseGroups(exam.id!);
                await examExerciseGroups.clickAddProgrammingExercise(exerciseGroup.id!);
                const uid = generateUUID();
                const programmingExerciseTitle = 'Programming ' + uid;
                const programmingExerciseShortName = 'programming' + uid;
                await programmingExerciseCreation.setTitle(programmingExerciseTitle);
                await programmingExerciseCreation.setShortName(programmingExerciseShortName);
                await programmingExerciseCreation.setPackageName('de.test');
                await programmingExerciseCreation.setPoints(10);
                const response = await programmingExerciseCreation.generate();
                expect(response.status()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, programmingExerciseTitle);
            });

            test('Edits an exercise group', async ({ page, examManagement, examExerciseGroups, examExerciseGroupCreation }) => {
                await page.goto(`/course-management/${course.id}/exams`);
                await examManagement.openExerciseGroups(exam.id!);
                await examExerciseGroups.shouldHaveTitle(exerciseGroup.id!, exerciseGroup.title!);
                await examExerciseGroups.clickEditGroup(exerciseGroup.id!);
                const newGroupName = 'Group 3';
                await examExerciseGroupCreation.typeTitle(newGroupName);
                await examExerciseGroupCreation.update();
                await examExerciseGroups.shouldHaveTitle(exerciseGroup.id!, newGroupName);
                exerciseGroup.title = newGroupName;
            });

            test('Delete an exercise group', async ({ page, navigationBar, courseManagement, examManagement, examExerciseGroups }) => {
                await page.goto('/');
                await navigationBar.openCourseManagement();
                await courseManagement.openExamsOfCourse(course.id!);
                await examManagement.openExerciseGroups(exam.id!);
                // If the group in the "Create group test" was created successfully, we delete it so there is no group with no exercise
                const group = exerciseGroup;
                await examExerciseGroups.clickDeleteGroup(group.id!, group.title!);
                await examExerciseGroups.shouldNotExist(group.id!);
            });

            test.afterEach(async ({ examAPIRequests }) => {
                await examAPIRequests.deleteExerciseGroupForExam(exam, exerciseGroup);
            });
        });

        test('Create exercise group', async ({ page, navigationBar, courseManagement, examManagement, examExerciseGroups, examExerciseGroupCreation }) => {
            await page.goto('/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExamsOfCourse(course.id!);
            await examManagement.openExerciseGroups(exam.id!);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(0);
            await examExerciseGroups.clickAddExerciseGroup();
            const groupName = 'Group 1';
            await examExerciseGroupCreation.typeTitle(groupName);
            await examExerciseGroupCreation.isMandatoryBoxShouldBeChecked();
            const group = await examExerciseGroupCreation.clickSave();
            // groupCount++;
            await examExerciseGroups.shouldHaveTitle(group.id!, groupName);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(1);
        });

        test.afterEach(async ({ courseManagementAPIRequests }) => {
            await courseManagementAPIRequests.deleteCourse(course, admin);
        });
    });

    test.describe.serial('Manage Students', () => {
        let exam: Exam;
        let course: Course;

        test.beforeAll('Create exam and exercises', async ({ browser }) => {
            const page = await newBrowserPage(browser);
            const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
            const examAPIRequests = new ExamAPIRequests(page);
            const exerciseAPIRequests = new ExerciseAPIRequests(page);

            await Commands.login(page, admin);
            course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            exam = await examAPIRequests.createExam({ course, title: 'Exam ' + generateUUID() });
            const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
            await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        });

        test.beforeEach(async ({ login }) => {
            await login(instructor);
        });

        test('Registers the course students for the exam', async ({ page, examManagement, studentExamManagement }) => {
            await page.goto(`/course-management/${course.id}/exams`);
            await examManagement.openStudentRegistration(exam.id!);
            const response = await studentExamManagement.clickRegisterCourseStudents();
            expect(response.status()).toBe(200);
            await studentExamManagement.checkStudent(studentOne.username);
        });

        test('Generates student exams', async ({ page, examManagement, studentExamManagement }) => {
            await page.goto(`/course-management/${course.id}/exams`);
            await examManagement.openStudentExams(exam.id!);
            await studentExamManagement.clickGenerateStudentExams();
            await page.waitForLoadState('networkidle');
            await expect(studentExamManagement.getGenerateMissingStudentExamsButton()).toBeDisabled();
        });

        test.afterAll(async ({ browser }) => {
            const page = await newBrowserPage(browser);
            const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
            await courseManagementAPIRequests.deleteCourse(course, admin);
        });
    });
});
