import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

import { admin, instructor, studentOne } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

// Common primitives
const uid = generateUUID();
const examTitle = 'test-exam' + uid;

test.describe('Test Exam management', () => {
    let course: Course;
    let exam: Exam;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests, examAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        exam = await examAPIRequests.createExam({ course, title: examTitle, testExam: true });
    });

    test.describe('Manage Group', () => {
        let exerciseGroup: ExerciseGroup;

        test.beforeEach(async ({ login, examAPIRequests }) => {
            await login(instructor);
            exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        });

        test('Create exercise group', async ({ page, navigationBar, courseManagement, examManagement, examExerciseGroups, examExerciseGroupCreation }) => {
            await page.goto('/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExamsOfCourse(course.id!);
            await examManagement.openExerciseGroups(exam.id!);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(1);
            await examExerciseGroups.clickAddExerciseGroup();
            const groupName = 'Group 1';
            await examExerciseGroupCreation.typeTitle(groupName);
            await examExerciseGroupCreation.isMandatoryBoxShouldBeChecked();
            const group = await examExerciseGroupCreation.clickSave();
            await examExerciseGroups.shouldHaveTitle(group.id!, groupName);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(2);
        });

        test('Adds a text exercise', async ({ page, examManagement, examExerciseGroups, textExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams`);
            await examManagement.openExerciseGroups(exam.id!);
            await examExerciseGroups.clickAddTextExercise(exerciseGroup.id!);
            const textExerciseTitle = 'text' + uid;
            await textExerciseCreation.typeTitle(textExerciseTitle);
            await textExerciseCreation.typeMaxPoints(10);
            const response = await textExerciseCreation.create();
            expect(response.status()).toBe(201);
            await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, textExerciseTitle);
        });

        test('Adds a quiz exercise', async ({ page, examManagement, examExerciseGroups, quizExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams`);
            await examManagement.openExerciseGroups(exam.id!);
            await examExerciseGroups.clickAddQuizExercise(exerciseGroup.id!);
            const quizExerciseTitle = 'quiz' + uid;
            await quizExerciseCreation.setTitle(quizExerciseTitle);
            await quizExerciseCreation.addMultipleChoiceQuestion(quizExerciseTitle, 10);
            const response = await quizExerciseCreation.saveQuiz();
            expect(response.status()).toBe(201);
            await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, quizExerciseTitle);
        });

        test('Adds a modeling exercise', async ({ page, examManagement, examExerciseGroups, modelingExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams`);
            await examManagement.openExerciseGroups(exam.id!);
            await examExerciseGroups.clickAddModelingExercise(exerciseGroup.id!);
            const modelingExerciseTitle = 'modeling' + uid;
            await modelingExerciseCreation.setTitle(modelingExerciseTitle);
            await modelingExerciseCreation.setPoints(10);
            const response = await modelingExerciseCreation.save();
            expect(response.status()).toBe(201);
            await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, modelingExerciseTitle);
        });

        test('Adds a programming exercise', async ({ page, examManagement, examExerciseGroups, programmingExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams`);
            await examManagement.openExerciseGroups(exam.id!);
            await examExerciseGroups.clickAddProgrammingExercise(exerciseGroup.id!);
            const programmingExerciseTitle = 'programming' + uid;
            await programmingExerciseCreation.setTitle(programmingExerciseTitle);
            await programmingExerciseCreation.setShortName(programmingExerciseTitle);
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
        });

        test('Delete an exercise group', async ({ page, navigationBar, courseManagement, examManagement, examExerciseGroups }) => {
            await page.goto('/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExamsOfCourse(course.id!);
            await examManagement.openExerciseGroups(exam.id!);
            await examExerciseGroups.clickDeleteGroup(exerciseGroup.id!, exerciseGroup.title!);
            await examExerciseGroups.shouldNotExist(exerciseGroup.id!);
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
