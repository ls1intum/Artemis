import dayjs from 'dayjs';

import { Course } from 'app/core/course/shared/entities/course.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';

import javaPartiallySuccessfulSubmission from '../../fixtures/exercise/programming/java/partially_successful/submission.json';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../fixtures/exercise/quiz/short_answer/template.json';
import { admin, instructor, studentOne } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { test } from '../../support/fixtures';
import { expect } from '@playwright/test';
import { Fixtures } from '../../fixtures/fixtures';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { QuizMode } from '../../support/constants';

test.describe('Import exercises', () => {
    let course: Course;
    let secondCourse: Course;
    let textExercise: TextExercise;
    let multipleChoiceQuizExercise: QuizExercise;
    let shortAnswerQuizExercise: QuizExercise;
    let modelingExercise: ModelingExercise;
    let programmingExercise: ProgrammingExercise;

    test.beforeEach('Setup course with exercises', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        textExercise = await exerciseAPIRequests.createTextExercise({ course });
        multipleChoiceQuizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate] });
        shortAnswerQuizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [shortAnswerQuizTemplate], quizMode: QuizMode.INDIVIDUAL });
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
        programmingExercise = await exerciseAPIRequests.createProgrammingExercise({ course });
        secondCourse = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(secondCourse, studentOne);
        await courseManagementAPIRequests.addInstructorToCourse(secondCourse, instructor);
    });

    test.describe('Imports exercises', () => {
        test('Imports text exercise', { tag: '@fast' }, async ({ login, page, courseManagementExercises, textExerciseCreation, courseOverview, textExerciseEditor }) => {
            await login(instructor, `/course-management/${secondCourse.id}/exercises`);
            await courseManagementExercises.importTextExercise();
            await courseManagementExercises.clickImportExercise(textExercise.id!);

            await textExerciseCreation.waitForFormToLoad();
            await expect(page.locator('#field_title')).toHaveValue(textExercise.title!);
            await expect(page.locator('#field_points')).toHaveValue(`${textExercise.maxPoints!}`);

            await textExerciseCreation.setReleaseDate(dayjs());
            await textExerciseCreation.setDueDate(dayjs().add(1, 'days'));
            await textExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));

            const importResponse = await textExerciseCreation.import();
            const exercise = await importResponse.json();
            await login(studentOne, `/courses/${secondCourse.id}/exercises/${exercise.id}`);
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

        test(
            'Imports multiple choice quiz exercise',
            { tag: '@fast' },
            async ({ login, page, courseManagementExercises, quizExerciseCreation, courseOverview, quizExerciseMultipleChoice }) => {
                await login(instructor, `/course-management/${secondCourse.id}/exercises`);
                await courseManagementExercises.importQuizExercise();
                await courseManagementExercises.clickImportExercise(multipleChoiceQuizExercise.id!);

                await quizExerciseCreation.waitForFormToLoad();
                await expect(page.locator('#field_title')).toHaveValue(multipleChoiceQuizExercise.title!);
                await expect(page.locator('#quiz-duration-minutes')).toHaveValue(`${multipleChoiceQuizExercise.duration! / 60}`);

                await quizExerciseCreation.setReleaseDate(dayjs());

                const importResponse = await quizExerciseCreation.import();
                const exercise: QuizExercise = await importResponse.json();
                await courseManagementExercises.startQuiz(exercise.id!);
                await login(studentOne, `/courses/${secondCourse.id}/exercises/${exercise.id}`);
                await courseOverview.startExercise(exercise.id!);
                await quizExerciseMultipleChoice.tickAnswerOption(exercise.id!, 0);
                await quizExerciseMultipleChoice.tickAnswerOption(exercise.id!, 2);
                const submitResponse = await quizExerciseMultipleChoice.submit();
                const submission: QuizSubmission = await submitResponse.json();
                expect(submission.submitted).toBe(true);
                expect(submitResponse.status()).toBe(200);
            },
        );

        test(
            'Imports short answer quiz exercise',
            { tag: '@fast' },
            async ({
                login,
                page,
                courseManagementExercises,
                quizExerciseCreation,
                courseOverview,
                quizExerciseShortAnswerQuiz,
                exerciseResult,
                navigationBar,
                courseManagement,
                quizExerciseParticipation,
            }) => {
                await login(instructor, `/course-management/${secondCourse.id}/exercises`);
                await courseManagementExercises.importQuizExercise();
                await courseManagementExercises.clickImportExercise(shortAnswerQuizExercise.id!);

                await quizExerciseCreation.waitForFormToLoad();
                await expect(page.locator('#field_title')).toHaveValue(shortAnswerQuizExercise.title!);
                await expect(page.locator('#quiz-duration-minutes')).toHaveValue(`${shortAnswerQuizExercise.duration! / 60}`);

                await quizExerciseCreation.setReleaseDate(dayjs());

                const importResponse = await quizExerciseCreation.import();
                const exercise: QuizExercise = await importResponse.json();
                const questionId = exercise.quizQuestions![0].id!;
                await login(studentOne, `/courses/${secondCourse.id}/exercises/${exercise.id}`);
                await courseOverview.openRunningExercise(exercise.id!);
                await quizExerciseParticipation.startIndividualQuizBatch();
                await page.waitForSelector('.quiz-waiting-for-start-overlay', { state: 'hidden' });
                await quizExerciseShortAnswerQuiz.typeAnswer(0, 1, questionId, 'give');
                await quizExerciseShortAnswerQuiz.typeAnswer(1, 1, questionId, 'let');
                await quizExerciseShortAnswerQuiz.typeAnswer(2, 1, questionId, 'run');
                await quizExerciseShortAnswerQuiz.typeAnswer(2, 3, questionId, 'desert');
                await quizExerciseShortAnswerQuiz.typeAnswer(3, 1, questionId, 'cry');
                await quizExerciseShortAnswerQuiz.typeAnswer(4, 1, questionId, 'goodbye');
                const submitResponse = await quizExerciseShortAnswerQuiz.submit();
                const submission: QuizSubmission = await submitResponse.json();
                expect(submission.submitted).toBe(true);
                expect(submitResponse.status()).toBe(200);

                await login(instructor, '/');
                await navigationBar.openCourseManagement();
                await courseManagement.openExercisesOfCourse(secondCourse.id!);
                await courseManagementExercises.endQuiz(exercise);

                await login(studentOne, `/courses/${secondCourse.id}/exercises/${exercise.id}`);
                await exerciseResult.shouldShowScore(100);
            },
        );

        test(
            'Imports modeling exercise',
            { tag: '@fast' },
            async ({ login, page, courseManagementExercises, modelingExerciseCreation, courseOverview, modelingExerciseEditor }) => {
                await login(instructor, `/course-management/${secondCourse.id}/exercises`);
                await courseManagementExercises.importModelingExercise();
                await courseManagementExercises.clickImportExercise(modelingExercise.id!);

                await modelingExerciseCreation.waitForFormToLoad();
                await expect(page.locator('#field_title')).toHaveValue(modelingExercise.title!);
                await expect(page.locator('#field_points')).toHaveValue(`${modelingExercise.maxPoints!}`);

                await modelingExerciseCreation.setReleaseDate(dayjs());
                await modelingExerciseCreation.setDueDate(dayjs().add(1, 'days'));
                await modelingExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));

                const importResponse = await modelingExerciseCreation.import();
                const exercise: ModelingExercise = await importResponse.json();
                await login(studentOne, `/courses/${secondCourse.id}/exercises/${exercise.id}`);
                await courseOverview.startExercise(exercise.id!);
                await courseOverview.openRunningExercise(exercise.id!);
                await modelingExerciseEditor.addComponentToModel(exercise.id!, 1);
                await modelingExerciseEditor.addComponentToModel(exercise.id!, 2);
                await modelingExerciseEditor.addComponentToModel(exercise.id!, 3);
                const submitResponse = await modelingExerciseEditor.submit();
                const submission: ModelingSubmission = await submitResponse.json();
                expect(submission.submitted).toBe(true);
                expect(submitResponse.status()).toBe(200);
            },
        );

        test(
            'Imports programming exercise',
            { tag: '@sequential' },
            async ({ login, page, courseManagementExercises, programmingExerciseCreation, courseOverview, programmingExerciseEditor }) => {
                await login(instructor, `/course-management/${secondCourse.id}/exercises`);
                await courseManagementExercises.importProgrammingExercise();
                await courseManagementExercises.clickImportExercise(programmingExercise.id!);

                await programmingExerciseCreation.waitForFormToLoad();
                await expect(page.locator('#field_points')).toHaveValue(`${programmingExercise.maxPoints!}`);

                await programmingExerciseCreation.setTitle('Import Test');
                await programmingExerciseCreation.setShortName('importtest' + generateUUID());
                await programmingExerciseCreation.setDueDate(dayjs().add(3, 'days'));

                const importResponse = await programmingExerciseCreation.import();
                const exercise: ProgrammingExercise = await importResponse.json();
                await login(studentOne, `/courses/${secondCourse.id}/exercises/${exercise.id}`);
                await courseOverview.startExercise(exercise.id!);
                await courseOverview.openRunningExercise(exercise.id!);
                await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, javaPartiallySuccessfulSubmission, async () => {
                    // Use exercise-scoped locator and check for text content
                    const resultScore = programmingExerciseEditor.getResultScoreFromExercise(exercise.id!);
                    await expect(resultScore).toContainText(javaPartiallySuccessfulSubmission.expectedResult, { timeout: 30000 });
                });
            },
        );
    });

    test.afterEach('Delete Courses', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
        await courseManagementAPIRequests.deleteCourse(secondCourse, admin);
    });
});
