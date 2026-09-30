import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

import { admin, instructor } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expectExerciseInGroup } from '../../../support/examGroupAssertions';
import { ExerciseType } from '../../../support/constants';
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

        test('Create exercise group', async ({ examAPIRequests, page, examExerciseGroups, examExerciseGroupCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(1);
            await examExerciseGroups.clickAddExerciseGroup();
            const groupName = 'Group 1';
            await examExerciseGroupCreation.typeTitle(groupName);
            await examExerciseGroupCreation.isMandatoryBoxShouldBeChecked();
            const group = await examExerciseGroupCreation.clickSave();
            await examExerciseGroups.shouldHaveTitle(group.id!, groupName);
            const storedNewGroup = (await examAPIRequests.getExerciseGroups(exam)).find((candidate) => candidate.id === group.id)!;
            expect(storedNewGroup.title).toBe(groupName);
            expect(storedNewGroup.isMandatory, 'the group is mandatory by default').toBe(true);
            expect(storedNewGroup.exercises ?? [], 'a new group has no exercises').toHaveLength(0);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(2);
        });

        test('Adds a text exercise', async ({ examAPIRequests, page, examExerciseGroups, textExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.clickAddTextExercise(exerciseGroup.id!);
            const textExerciseTitle = 'text' + uid;
            await textExerciseCreation.setTitle(textExerciseTitle);
            await textExerciseCreation.typeMaxPoints(10);
            const response = await textExerciseCreation.create();
            expect(response.status()).toBe(201);
            await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, textExerciseTitle);
            await expectExerciseInGroup(examAPIRequests, exam, exerciseGroup, { title: textExerciseTitle, type: ExerciseType.TEXT, maxPoints: 10 });
        });

        test('Adds a quiz exercise', async ({ examAPIRequests, page, examExerciseGroups, quizExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.clickAddQuizExercise(exerciseGroup.id!);
            const quizExerciseTitle = 'quiz' + uid;
            await quizExerciseCreation.setTitle(quizExerciseTitle);
            await quizExerciseCreation.addMultipleChoiceQuestion(quizExerciseTitle, 10);
            const response = await quizExerciseCreation.saveQuiz();
            expect(response.status()).toBe(201);
            await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, quizExerciseTitle);
            await expectExerciseInGroup(examAPIRequests, exam, exerciseGroup, { title: quizExerciseTitle, type: ExerciseType.QUIZ, maxPoints: 10 });
        });

        test('Adds a modeling exercise', async ({ examAPIRequests, page, examExerciseGroups, modelingExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.clickAddModelingExercise(exerciseGroup.id!);
            const modelingExerciseTitle = 'modeling' + uid;
            await modelingExerciseCreation.setTitle(modelingExerciseTitle);
            await modelingExerciseCreation.setPoints(10);
            const response = await modelingExerciseCreation.save();
            expect(response.status()).toBe(201);
            await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, modelingExerciseTitle);
            await expectExerciseInGroup(examAPIRequests, exam, exerciseGroup, { title: modelingExerciseTitle, type: ExerciseType.MODELING, maxPoints: 10 });
        });

        test('Adds a programming exercise', async ({ examAPIRequests, page, examExerciseGroups, programmingExerciseCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await page.waitForLoadState('domcontentloaded');
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
            await expectExerciseInGroup(examAPIRequests, exam, exerciseGroup, { title: programmingExerciseTitle, type: ExerciseType.PROGRAMMING, maxPoints: 10 });
        });

        test('Edits an exercise group', async ({ examAPIRequests, page, examExerciseGroups, examExerciseGroupCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.shouldHaveTitle(exerciseGroup.id!, exerciseGroup.title!);
            await examExerciseGroups.clickEditGroup(exerciseGroup.id!);
            const newGroupName = 'Group 3';
            await examExerciseGroupCreation.typeTitle(newGroupName);
            await examExerciseGroupCreation.update();
            await examExerciseGroups.shouldHaveTitle(exerciseGroup.id!, newGroupName);
            const storedGroup = (await examAPIRequests.getExerciseGroups(exam)).find((candidate) => candidate.id === exerciseGroup.id)!;
            expect(storedGroup.title, 'the renamed group is stored').toBe(newGroupName);
            expect(storedGroup.isMandatory, 'renaming does not change whether the group is mandatory').toBe(exerciseGroup.isMandatory);
        });

        test('Delete an exercise group', async ({ examAPIRequests, page, examExerciseGroups }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.clickDeleteGroup(exerciseGroup.id!, exerciseGroup.title!);
            await examExerciseGroups.shouldNotExist(exerciseGroup.id!);
            expect(
                (await examAPIRequests.getExerciseGroups(exam)).map((candidate) => candidate.id),
                'the server no longer has the group',
            ).not.toContain(exerciseGroup.id);
        });
    });

    test.afterEach('Delete exam', async ({ examAPIRequests }) => {
        await examAPIRequests.deleteExam(exam);
    });
});
