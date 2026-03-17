import { expect } from '@playwright/test';
import { admin, instructor, studentOne } from '../../support/users';
import { generateUUID, newBrowserPage } from '../../support/utils';
import { test } from '../../support/fixtures';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';
import { SEED_COURSES } from '../../support/seedData';

const course = { id: SEED_COURSES.examManagement.id } as any;

test.describe('Exam management', { tag: '@fast' }, () => {
    test.describe('Exercise group', () => {
        let exam: Exam;

        test.beforeEach('Create exam', async ({ login, examAPIRequests }) => {
            await login(admin);
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

            test('Adds a text exercise', async ({ page, textExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
                await examExerciseGroups.clickAddTextExercise(exerciseGroup.id!);
                const textExerciseTitle = 'Text ' + generateUUID();
                await textExerciseCreation.setTitle(textExerciseTitle);
                await textExerciseCreation.typeMaxPoints(10);
                const response = await textExerciseCreation.create();
                expect(response.status()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, textExerciseTitle);
            });

            test('Adds a quiz exercise', async ({ page, quizExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
                await examExerciseGroups.clickAddQuizExercise(exerciseGroup.id!);
                const quizExerciseTitle = 'Quiz ' + generateUUID();
                await quizExerciseCreation.setTitle(quizExerciseTitle);
                await quizExerciseCreation.addMultipleChoiceQuestion(quizExerciseTitle, 10);
                const response = await quizExerciseCreation.saveQuiz();
                expect(response.status()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, quizExerciseTitle);
            });

            test('Adds a modeling exercise', async ({ page, modelingExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
                await examExerciseGroups.clickAddModelingExercise(exerciseGroup.id!);
                const modelingExerciseTitle = 'Modeling ' + generateUUID();
                await modelingExerciseCreation.setTitle(modelingExerciseTitle);
                await modelingExerciseCreation.setPoints(10);
                const response = await modelingExerciseCreation.save();
                expect(response.status()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, modelingExerciseTitle);
            });

            test('Adds a programming exercise', async ({ page, programmingExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
                await examExerciseGroups.clickAddProgrammingExercise(exerciseGroup.id!);
                const uid = generateUUID();
                const programmingExerciseTitle = 'Programming ' + uid;
                const programmingExerciseShortName = 'programming' + uid;
                await programmingExerciseCreation.changeEditMode();
                await programmingExerciseCreation.setTitle(programmingExerciseTitle);
                await programmingExerciseCreation.setShortName(programmingExerciseShortName);
                await programmingExerciseCreation.setPackageName('de.test');
                await programmingExerciseCreation.setPoints(10);
                const response = await programmingExerciseCreation.generate();
                expect(response.status()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, programmingExerciseTitle);
            });

            test('Edits an exercise group', async ({ page, examExerciseGroups, examExerciseGroupCreation }) => {
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
                await examExerciseGroups.shouldHaveTitle(exerciseGroup.id!, exerciseGroup.title!);
                await examExerciseGroups.clickEditGroup(exerciseGroup.id!);
                const newGroupName = 'Group 3';
                await examExerciseGroupCreation.typeTitle(newGroupName);
                await examExerciseGroupCreation.update();
                await examExerciseGroups.shouldHaveTitle(exerciseGroup.id!, newGroupName);
                exerciseGroup.title = newGroupName;
            });

            test('Delete an exercise group', async ({ page, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
                // If the group in the "Create group test" was created successfully, we delete it so there is no group with no exercise
                const group = exerciseGroup;
                await examExerciseGroups.clickDeleteGroup(group.id!, group.title!);
                await examExerciseGroups.shouldNotExist(group.id!);
            });

            test.afterEach(async ({ examAPIRequests }) => {
                await examAPIRequests.deleteExerciseGroupForExam(exam, exerciseGroup);
            });
        });

        test('Create exercise group', async ({ page, examExerciseGroups, examExerciseGroupCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(0);
            await examExerciseGroups.clickAddExerciseGroup();
            const groupName = 'Group 1';
            await examExerciseGroupCreation.typeTitle(groupName);
            await examExerciseGroupCreation.isMandatoryBoxShouldBeChecked();
            const group = await examExerciseGroupCreation.clickSave();
            await examExerciseGroups.shouldHaveTitle(group.id!, groupName);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(1);
        });

        test.afterEach('Delete exam', async ({ examAPIRequests }) => {
            await examAPIRequests.deleteExam(exam);
        });
    });

    test.describe.serial('Manage Students', () => {
        let exam: Exam;

        test.beforeAll('Create exam and exercises', async ({ browser }) => {
            const page = await newBrowserPage(browser);
            const examAPIRequests = new ExamAPIRequests(page);
            const exerciseAPIRequests = new ExerciseAPIRequests(page);

            await Commands.login(page, admin);
            exam = await examAPIRequests.createExam({ course, title: 'Exam ' + generateUUID() });
            const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
            await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        });

        test.beforeEach(async ({ login }) => {
            await login(instructor);
        });

        test('Registers the course students for the exam', async ({ page, studentExamManagement }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/students`);
            const response = await studentExamManagement.clickRegisterCourseStudents();
            expect(response.status()).toBe(200);
            await studentExamManagement.checkStudent(studentOne.username);
        });

        test('Generates student exams', async ({ page, studentExamManagement }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/student-exams`);
            await studentExamManagement.clickGenerateStudentExams();
            await page.waitForLoadState('domcontentloaded');
            await expect(studentExamManagement.getGenerateMissingStudentExamsButton()).toBeDisabled();
        });

        test.afterAll('Delete exam', async ({ browser }) => {
            const page = await newBrowserPage(browser);
            const examAPIRequests = new ExamAPIRequests(page);
            await Commands.login(page, admin);
            await examAPIRequests.deleteExam(exam);
        });
    });
});
