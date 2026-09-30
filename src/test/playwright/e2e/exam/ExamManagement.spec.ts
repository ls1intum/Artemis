import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { admin, instructor, studentOne } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { test } from '../../support/fixtures';
import { expectExerciseInGroup } from '../../support/examGroupAssertions';
import { ExerciseType } from '../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
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

            test('Adds a text exercise', async ({ examAPIRequests, page, textExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
                await examExerciseGroups.clickAddTextExercise(exerciseGroup.id!);
                const textExerciseTitle = 'Text ' + generateUUID();
                await textExerciseCreation.setTitle(textExerciseTitle);
                await textExerciseCreation.typeMaxPoints(10);
                const response = await textExerciseCreation.create();
                expect(response.status(), await response.text()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, textExerciseTitle);
                await expectExerciseInGroup(examAPIRequests, exam, exerciseGroup, { title: textExerciseTitle, type: ExerciseType.TEXT, maxPoints: 10 });
            });

            test('Adds a quiz exercise', async ({ examAPIRequests, page, quizExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
                await examExerciseGroups.clickAddQuizExercise(exerciseGroup.id!);
                const quizExerciseTitle = 'Quiz ' + generateUUID();
                await quizExerciseCreation.setTitle(quizExerciseTitle);
                await quizExerciseCreation.addMultipleChoiceQuestion(quizExerciseTitle, 10);
                const response = await quizExerciseCreation.saveQuiz();
                expect(response.status(), await response.text()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, quizExerciseTitle);
                await expectExerciseInGroup(examAPIRequests, exam, exerciseGroup, { title: quizExerciseTitle, type: ExerciseType.QUIZ, maxPoints: 10 });
            });

            test('Adds a modeling exercise', async ({ examAPIRequests, page, modelingExerciseCreation, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
                await examExerciseGroups.clickAddModelingExercise(exerciseGroup.id!);
                const modelingExerciseTitle = 'Modeling ' + generateUUID();
                await modelingExerciseCreation.setTitle(modelingExerciseTitle);
                await modelingExerciseCreation.setPoints(10);
                const response = await modelingExerciseCreation.save();
                expect(response.status(), await response.text()).toBe(201);
                await examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
                await examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, modelingExerciseTitle);
                await expectExerciseInGroup(examAPIRequests, exam, exerciseGroup, { title: modelingExerciseTitle, type: ExerciseType.MODELING, maxPoints: 10 });
            });

            test('Adds a programming exercise', async ({ examAPIRequests, page, programmingExerciseCreation, examExerciseGroups }) => {
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
                expect(response.status(), await response.text()).toBe(201);
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
                exerciseGroup.title = newGroupName;
            });

            test('Delete an exercise group', async ({ examAPIRequests, page, examExerciseGroups }) => {
                await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
                // If the group in the "Create group test" was created successfully, we delete it so there is no group with no exercise
                const group = exerciseGroup;
                await examExerciseGroups.clickDeleteGroup(group.id!, group.title!);
                await examExerciseGroups.shouldNotExist(group.id!);
                expect(
                    (await examAPIRequests.getExerciseGroups(exam)).map((candidate) => candidate.id),
                    'the server no longer has the group',
                ).not.toContain(group.id);
            });

            test.afterEach(async ({ examAPIRequests }) => {
                await examAPIRequests.deleteExerciseGroupForExam(exam, exerciseGroup);
            });
        });

        test('Create exercise group', async ({ examAPIRequests, page, examExerciseGroups, examExerciseGroupCreation }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/exercise-groups`);
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(0);
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
            await examExerciseGroups.shouldShowNumberOfExerciseGroups(1);
        });

        test.afterEach('Delete exam', async ({ examAPIRequests }) => {
            await examAPIRequests.deleteExam(exam);
        });
    });

    test.describe('Manage Students', () => {
        let exam: Exam;

        // Every test gets an exam of its own, so the tests do not depend on each other or on their order.
        test.beforeEach('Create exam and exercises', async ({ login, examAPIRequests, exerciseAPIRequests }) => {
            await login(admin);
            exam = await examAPIRequests.createExam({ course, title: 'Exam ' + generateUUID() });
            const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
            await exerciseAPIRequests.createTextExercise({ exerciseGroup });
            await login(instructor);
        });

        test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
            await login(admin);
            await examAPIRequests.deleteExam(exam);
        });

        test('Registers the course students for the exam', async ({ page, studentExamManagement }) => {
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/students`);
            const response = await studentExamManagement.clickRegisterCourseStudents();
            expect(response.status()).toBe(200);
            await studentExamManagement.checkStudent(studentOne.username);
            // Every student of the course is now registered, not just the first one.
            const registeredRows = await studentExamManagement.getStudentExamRows().count();
            expect(registeredRows).toBeGreaterThanOrEqual(4);
        });

        test('Generates student exams', async ({ page, login, examAPIRequests, studentExamManagement }) => {
            await login(admin);
            await examAPIRequests.registerAllCourseStudentsForExam(exam);
            await login(instructor);
            await page.goto(`/course-management/${course.id}/exams/${exam.id!}/students`);
            await expect(studentExamManagement.getStudentExamRows().first()).toBeVisible();
            const registeredStudents = await studentExamManagement.getStudentExamRows().count();
            expect(registeredStudents).toBeGreaterThanOrEqual(4);

            const response = await studentExamManagement.clickGenerateStudentExams();
            expect(response.status()).toBe(200);

            // Exactly one untouched student exam per registered student, with the working time of the exam.
            const studentExams = await examAPIRequests.getAllStudentExams(exam);
            expect(studentExams).toHaveLength(registeredStudents);
            expect(await response.json()).toHaveLength(registeredStudents);
            const duration = dayjs(exam.endDate as any).diff(dayjs(exam.startDate as any), 'seconds');
            for (const studentExam of studentExams) {
                expect(studentExam.testRun).toBe(false);
                expect(studentExam.submitted).toBe(false);
                expect(studentExam.workingTime).toBe(duration);
            }

            // Nothing is missing any more, so the menu no longer offers to generate the missing ones.
            await page.waitForLoadState('domcontentloaded');
            await studentExamManagement.openManageStudentExamsMenu();
            await expect(studentExamManagement.getGenerateMissingStudentExamsButton()).toBeDisabled();
        });
    });
});
