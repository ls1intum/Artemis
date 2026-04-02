import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

import { admin, instructor } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

// Common primitives
const uid = generateUUID();
const examTitle = 'test-exam' + uid;

const course = { id: SEED_COURSES.testExam.id } as any;

test.describe('Test Exam management', { tag: '@fast' }, () => {
    let exam: Exam;

    test.beforeEach('Create exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({ course, title: examTitle, testExam: true });
    });

    test.describe('Manage Group', () => {
        let exerciseGroup: ExerciseGroup;

        test.beforeEach(async ({ login, examAPIRequests }) => {
            await login(instructor);
            exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        });

        test('Create exercise group', async ({ page, examExerciseGroups, examExerciseGroupCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(1);
            await examExerciseGroups.clickAddExerciseGroup();
            const groupName = 'Group 1';
            await examExerciseGroupCreation.typeTitle(groupName);
            await examExerciseGroupCreation.isMandatoryBoxShouldBeChecked();
            const group = await examExerciseGroupCreation.clickSave();
            await examExerciseGroups.shouldHaveTitle(group.id!, groupName);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(2);
        });

        test('Adds a text exercise', async ({ page, examExerciseGroups, textExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.clickAddTextExercise(exerciseGroup.id!);
            const textExerciseTitle = 'text' + uid;
            await textExerciseCreation.setTitle(textExerciseTitle);
            await textExerciseCreation.typeMaxPoints(10);
            const response = await textExerciseCreation.create();
            expect(response.status()).toBe(201);
            await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, textExerciseTitle);
        });

        test('Adds a quiz exercise', async ({ page, examExerciseGroups, quizExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.clickAddQuizExercise(exerciseGroup.id!);
            const quizExerciseTitle = 'quiz' + uid;
            await quizExerciseCreation.setTitle(quizExerciseTitle);
            await quizExerciseCreation.addMultipleChoiceQuestion(quizExerciseTitle, 10);
            const response = await quizExerciseCreation.saveQuiz();
            expect(response.status()).toBe(201);
            await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, quizExerciseTitle);
        });

        test('Adds a modeling exercise', async ({ page, examExerciseGroups, modelingExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.clickAddModelingExercise(exerciseGroup.id!);
            const modelingExerciseTitle = 'modeling' + uid;
            await modelingExerciseCreation.setTitle(modelingExerciseTitle);
            await modelingExerciseCreation.setPoints(10);
            const response = await modelingExerciseCreation.save();
            expect(response.status()).toBe(201);
            await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, modelingExerciseTitle);
        });

        test('Adds a programming exercise', async ({ page, examExerciseGroups, programmingExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await page.waitForLoadState('networkidle');
            await examExerciseGroups.clickAddProgrammingExercise(exerciseGroup.id!);
            const programmingExerciseTitle = 'programming' + uid;
            await programmingExerciseCreation.changeEditMode();
            await programmingExerciseCreation.setTitle(programmingExerciseTitle);
            await programmingExerciseCreation.setShortName(programmingExerciseTitle);
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
            await examExerciseGroups.clickEditGroupForTestExam();
            const newGroupName = 'Group 3';
            await examExerciseGroupCreation.typeTitle(newGroupName);
            await examExerciseGroupCreation.update();
            await examExerciseGroups.shouldHaveTitle(exerciseGroup.id!, newGroupName);
        });

        test('Delete an exercise group', async ({ page, examExerciseGroups }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.clickDeleteGroup(exerciseGroup.id!, exerciseGroup.title!);
            await examExerciseGroups.shouldNotExist(exerciseGroup.id!);
        });
    });

    test.afterEach('Delete exam', async ({ examAPIRequests }) => {
        await examAPIRequests.deleteExam(exam);
    });
});
