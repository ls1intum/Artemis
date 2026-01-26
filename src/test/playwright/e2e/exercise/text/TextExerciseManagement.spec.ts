import { Course } from 'app/core/course/shared/entities/course.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { test } from '../../../support/fixtures';
import { admin } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import dayjs from 'dayjs';
import { expect } from '@playwright/test';
import { ExampleParticipation } from 'app/exercise/shared/entities/participation/example-participation.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';

test.describe('Text exercise management', { tag: '@fast' }, () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
    });

    test('Creates a text exercise in the UI', async ({
        page,
        navigationBar,
        courseManagement,
        courseManagementExercises,
        textExerciseCreation,
        textExerciseExampleSubmissions,
        textExerciseExampleSubmissionCreation,
    }) => {
        await page.goto('/');
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

        // Create an example submission
        await courseManagementExercises.clickExampleSubmissionsButton();
        await textExerciseExampleSubmissions.clickCreateExampleSubmission();
        await textExerciseExampleSubmissionCreation.showsExerciseTitle(exerciseTitle);
        await textExerciseExampleSubmissionCreation.showsProblemStatement(problemStatement);
        await textExerciseExampleSubmissionCreation.showsExampleSolution(exampleSolution);
        const submission = 'This is an\nexample\nsubmission';
        await textExerciseExampleSubmissionCreation.typeExampleSubmission(submission);

        const submissionCreationResponse = await textExerciseExampleSubmissionCreation.clickCreateNewExampleSubmission();
        const exampleParticipation: ExampleParticipation = await submissionCreationResponse.json();
        // ExampleParticipation inherits from Participation, so submission is in the submissions array
        const textSubmission: TextSubmission = exampleParticipation.submissions![0] as TextSubmission;
        expect(submissionCreationResponse.status()).toBe(200);
        expect(textSubmission.text).toBe(submission);

        // Make sure text exercise is shown in exercises list
        await page.goto(`course-management/${course.id}/exercises`);
        await expect(courseManagementExercises.getExercise(exercise.id!)).toBeVisible();
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

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
