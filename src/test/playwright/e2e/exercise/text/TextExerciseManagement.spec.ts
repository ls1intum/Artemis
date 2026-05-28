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
            // Login → openCourseManagement → openExercises → createTextExercise + form +
            // example-submission flow + up-to-three 20s polling reloads on the exercises list
            // exceeds the 90s @slow budget under multi-node CI load. Lift the per-test
            // timeout to 270s via test.slow() — observed worst case ~200s.
            test.slow();
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

            // Make sure text exercise is shown in exercises list. Under heavy parallel CI
            // load the exercises-list response occasionally lags behind the create response,
            // so the freshly created card isn't in the first render. Reload up to three
            // times before failing — each reload re-fetches the list, and the server-side
            // commit reliably propagates within a few seconds.
            const exerciseUrl = `/course-management/${course.id}/exercises`;
            const card = courseManagementExercises.getExercise(exercise.id!);
            const visibleWithin = async (timeout: number): Promise<boolean> =>
                card
                    .waitFor({ state: 'visible', timeout })
                    .then(() => true)
                    .catch(() => false);
            await page.goto(exerciseUrl);
            await page.waitForLoadState('load');
            for (let attempt = 0; attempt < 3; attempt++) {
                if (await visibleWithin(20_000)) {
                    break;
                }
                if (attempt === 2) {
                    throw new Error(`Newly created text exercise card #exercise-card-${exercise.id!} did not appear after 3 reloads`);
                }
                await page.reload();
                await page.waitForLoadState('load');
            }
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
