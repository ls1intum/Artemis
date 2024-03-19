import dayjs from 'dayjs';

import { Course } from 'app/entities/course.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';

import javaPartiallySuccessfulSubmission from '../../fixtures/exercise/programming/java/partially_successful/submission.json';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import { admin, instructor, studentOne } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { test } from '../../support/fixtures';
import { expect } from '@playwright/test';
import { Fixtures } from '../../fixtures/fixtures';
import { TextSubmission } from 'app/entities/text-submission.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';

test.describe('Import exercises', () => {
    let course: Course;
    let secondCourse: Course;
    let textExercise: TextExercise;
    let quizExercise: QuizExercise;
    let modelingExercise: ModelingExercise;
    let programmingExercise: ProgrammingExercise;

    test.beforeEach('Setup course with exercises', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        textExercise = await exerciseAPIRequests.createTextExercise({ course });
        quizExercise = await exerciseAPIRequests.createQuizExercise({ course }, [multipleChoiceQuizTemplate]);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
        programmingExercise = await exerciseAPIRequests.createProgrammingExercise({ course });
        secondCourse = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(secondCourse, studentOne);
        await courseManagementAPIRequests.addInstructorToCourse(secondCourse, instructor);
    });

    test.describe('Imports exercises', () => {
        test('Imports text exercise', async ({ login, page, courseManagementExercises, textExerciseCreation, courseOverview, textExerciseEditor }) => {
            await login(instructor, `/course-management/${secondCourse.id}/exercises`);
            await courseManagementExercises.importTextExercise();
            await courseManagementExercises.clickImportExercise(textExercise.id!);

            await expect(page.locator('#field_title')).toHaveValue(textExercise.title!);
            await expect(page.locator('#field_points')).toHaveValue(`${textExercise.maxPoints!}`);

            await textExerciseCreation.setReleaseDate(dayjs());
            await textExerciseCreation.setDueDate(dayjs().add(1, 'days'));
            await textExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));

            const importResponse = await textExerciseCreation.import();
            const exercise = await importResponse.json();
            await login(studentOne, `/courses/${secondCourse.id}`);
            await courseOverview.startExercise(exercise.id!);
            await courseOverview.openRunningExercise(exercise.id!);
            const submissionText = await Fixtures.get('loremIpsum-short.txt');
            await textExerciseEditor.shouldShowNumberOfWords(0);
            await textExerciseEditor.shouldShowNumberOfCharacters(0);
            await textExerciseEditor.typeSubmission(exercise.id!, submissionText!);
            await textExerciseEditor.shouldShowNumberOfWords(16);
            await textExerciseEditor.shouldShowNumberOfCharacters(83);
            const submissionResponse = await textExerciseEditor.submit();
            const submission: TextSubmission = await submissionResponse.json();
            expect(submission.text).toBe(submissionText);
            expect(submission.submitted).toBe(true);
            expect(submissionResponse.status()).toBe(200);
        });

        test('Imports quiz exercise', async ({ login, page, courseManagementExercises, quizExerciseCreation, courseOverview, quizExerciseMultipleChoice }) => {
            await login(instructor, `/course-management/${secondCourse.id}/exercises`);
            await courseManagementExercises.importQuizExercise();
            await courseManagementExercises.clickImportExercise(quizExercise.id!);

            await expect(page.locator('#field_title')).toHaveValue(quizExercise.title!);
            await expect(page.locator('#quiz-duration-minutes')).toHaveValue(`${quizExercise.duration! / 60}`);

            // Timeout commented to check if it is necessary
            // page.waitForTimeout(500);

            await quizExerciseCreation.setVisibleFrom(dayjs());

            const importResponse = await quizExerciseCreation.import();
            const exercise: QuizExercise = await importResponse.json();
            await courseManagementExercises.startQuiz(exercise.id!);
            await login(studentOne, `/courses/${secondCourse.id}`);
            await courseOverview.startExercise(exercise.id!);
            await quizExerciseMultipleChoice.tickAnswerOption(exercise.id!, 0);
            await quizExerciseMultipleChoice.tickAnswerOption(exercise.id!, 2);
            const submitResponse = await quizExerciseMultipleChoice.submit();
            const submission: QuizSubmission = await submitResponse.json();
            expect(submission.submitted).toBe(true);
            expect(submitResponse.status()).toBe(200);
        });

        test('Imports modeling exercise', async ({ login, page, courseManagementExercises, modelingExerciseCreation, courseOverview, modelingExerciseEditor }) => {
            await login(instructor, `/course-management/${secondCourse.id}/exercises`);
            await courseManagementExercises.importModelingExercise();
            await courseManagementExercises.clickImportExercise(modelingExercise.id!);

            await page.waitForTimeout(10000);
            await expect(page.locator('#field_title')).toHaveValue(modelingExercise.title!);
            await expect(page.locator('#field_points')).toHaveValue(`${modelingExercise.maxPoints!}`);

            await modelingExerciseCreation.setReleaseDate(dayjs());
            await modelingExerciseCreation.setDueDate(dayjs().add(1, 'days'));
            await modelingExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));

            const importResponse = await modelingExerciseCreation.import();
            const exercise: ModelingExercise = await importResponse.json();
            await login(studentOne, `/courses/${secondCourse.id}`);
            await courseOverview.startExercise(exercise.id!);
            await courseOverview.openRunningExercise(exercise.id!);
            await modelingExerciseEditor.addComponentToModel(exercise.id!, 1);
            await modelingExerciseEditor.addComponentToModel(exercise.id!, 2);
            await modelingExerciseEditor.addComponentToModel(exercise.id!, 3);
            const submitResponse = await modelingExerciseEditor.submit();
            const submission: ModelingSubmission = await submitResponse.json();
            expect(submission.submitted).toBe(true);
            expect(submitResponse.status()).toBe(200);
        });

        test('Imports programming exercise', async ({ login, page, courseManagementExercises, programmingExerciseCreation, courseOverview, programmingExerciseEditor }) => {
            await login(instructor, `/course-management/${secondCourse.id}/exercises`);
            await courseManagementExercises.importProgrammingExercise();
            await courseManagementExercises.clickImportExercise(programmingExercise.id!);

            await expect(page.locator('#field_points')).toHaveValue(`${programmingExercise.maxPoints!}`);

            await programmingExerciseCreation.setTitle('Import Test');
            await programmingExerciseCreation.setShortName('importtest' + generateUUID());
            await programmingExerciseCreation.setDueDate(dayjs().add(3, 'days'));

            const importResponse = await programmingExerciseCreation.import();
            const exercise: ProgrammingExercise = await importResponse.json();
            await login(studentOne, `/courses/${secondCourse.id}`);
            await courseOverview.startExercise(exercise.id!);
            await courseOverview.openRunningExercise(exercise.id!);
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, javaPartiallySuccessfulSubmission, async () => {
                const resultScore = await programmingExerciseEditor.getResultScore();
                await expect(resultScore.getByText(javaPartiallySuccessfulSubmission.expectedResult)).toBeVisible();
            });
        });
    });

    test.afterEach('Delete Courses', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
        await courseManagementAPIRequests.deleteCourse(secondCourse, admin);
    });
});
