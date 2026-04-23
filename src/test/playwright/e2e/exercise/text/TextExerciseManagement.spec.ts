import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { test } from '../../../support/fixtures';
import { admin } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import dayjs from 'dayjs';
import { expect } from '@playwright/test';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

test.describe('Text exercise management', { tag: '@slow' }, () => {
    test.describe('Text exercise creation', () => {
        let createdExerciseId: number | undefined;

        test('Creates a text exercise in the UI', async ({
            login,
            page,
            navigationBar,
            courseManagement,
            courseManagementExercises,
            textExerciseCreation,
            textExerciseExampleSubmissions,
            textExerciseExampleSubmissionCreation,
        }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.createTextExercise();

            // Fill out text exercise form
            const exerciseTitle = 'text exercise' + generateUUID();
            await textExerciseCreation.setTitle(exerciseTitle);
            await textExerciseCreation.setReleaseDate(dayjs());
            await textExerciseCreation.setDueDate(dayjs().add(1, 'days'));
            await textExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));
            await textExerciseCreation.typeMaxPoints(10);
            const problemStatement = 'This is a problem statement';
            const exampleSolution = 'E = mc^2';
            await textExerciseCreation.typeProblemStatement(problemStatement);
            await textExerciseCreation.typeExampleSolution(exampleSolution);
            const exerciseCreateResponse = await textExerciseCreation.create();
            const exercise: TextExercise = await exerciseCreateResponse.json();
            createdExerciseId = exercise.id;

            // Create an example submission
            await courseManagementExercises.clickExampleSubmissionsButton();
            await textExerciseExampleSubmissions.clickCreateExampleSubmission();
            await textExerciseExampleSubmissionCreation.showsExerciseTitle(exerciseTitle);
            await textExerciseExampleSubmissionCreation.showsProblemStatement(problemStatement);
            await textExerciseExampleSubmissionCreation.showsExampleSolution(exampleSolution);
            const submission = 'This is an\nexample\nsubmission';
            await textExerciseExampleSubmissionCreation.typeExampleSubmission(submission);

            const submissionCreationResponse = await textExerciseExampleSubmissionCreation.clickCreateNewExampleSubmission();
            const exampleSubmission: ExampleSubmission = await submissionCreationResponse.json();
            const textSubmission: TextSubmission = exampleSubmission.submission!;
            expect(submissionCreationResponse.status()).toBe(200);
            expect(textSubmission.text).toBe(submission);

            // Make sure text exercise is shown in exercises list
            await page.goto(`/course-management/${course.id}/exercises`);
            await page.waitForLoadState('networkidle');
            await expect(courseManagementExercises.getExercise(exercise.id!)).toBeVisible();
        });

        test.afterEach('Delete created exercise', async ({ login, exerciseAPIRequests }) => {
            if (createdExerciseId) {
                await login(admin);
                await exerciseAPIRequests.deleteTextExercise(createdExerciseId);
                createdExerciseId = undefined;
            }
        });
    });

    test.describe('Text exercise deletion', () => {
        let exercise: TextExercise;

        test.beforeEach('Create text exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin, '/');
            exercise = await exerciseAPIRequests.createTextExercise({ course });
        });

        test('Deletes an existing text exercise', async ({ login, navigationBar, courseManagement, courseManagementExercises }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.deleteTextExercise(exercise);
            await expect(courseManagementExercises.getExercise(exercise.id!)).not.toBeAttached();
        });
    });

    // Seed courses are persistent — no cleanup needed
});
