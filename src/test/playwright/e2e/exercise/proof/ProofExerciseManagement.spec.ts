import { test } from '../../../support/fixtures';
import { admin } from '../../../support/users';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

/**
 * Smoke E2E coverage for the proof-exercise management surface.
 *
 * Proof exercises are course-only by design (the exam-mode integration was stripped), so this
 * spec exercises only the course-scoped REST + management list flow. UI creation through the
 * block-builder is not yet covered — it requires a custom Playwright page object and is tracked
 * as a separate milestone.
 */
test.describe('Proof exercise management', { tag: '@slow' }, () => {
    test.describe('Proof exercise creation via API', () => {
        let createdExerciseId: number | undefined;

        test('Creates a proof exercise via the REST endpoint and shows it in the management list', async ({
            login,
            page,
            navigationBar,
            courseManagement,
            courseManagementExercises,
            exerciseAPIRequests,
        }) => {
            test.slow();
            await login(admin, '/');
            const exercise = await exerciseAPIRequests.createProofExercise({ course });
            expect(exercise.id).toBeTruthy();
            createdExerciseId = exercise.id;

            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);

            const card = courseManagementExercises.getExercise(exercise.id!);
            const visibleWithin = async (timeout: number): Promise<boolean> =>
                card
                    .waitFor({ state: 'visible', timeout })
                    .then(() => true)
                    .catch(() => false);

            for (let attempt = 0; attempt < 3; attempt++) {
                if (await visibleWithin(20_000)) {
                    break;
                }
                if (attempt === 2) {
                    throw new Error(`Newly created proof exercise card #exercise-card-${exercise.id!} did not appear after 3 reloads`);
                }
                await page.reload();
                await page.waitForLoadState('load');
            }
        });

        test.afterEach('Delete created proof exercise', async ({ login, exerciseAPIRequests }) => {
            if (createdExerciseId) {
                await login(admin);
                await exerciseAPIRequests.deleteProofExercise(createdExerciseId);
                createdExerciseId = undefined;
            }
        });
    });

    test.describe('Proof exercise deletion', () => {
        let exerciseId: number | undefined;

        test.beforeEach('Create proof exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin, '/');
            const exercise = await exerciseAPIRequests.createProofExercise({ course });
            exerciseId = exercise.id;
        });

        test('Deletes a proof exercise via the REST endpoint', async ({ login, exerciseAPIRequests }) => {
            test.skip(!exerciseId, 'creation failed');
            await login(admin, '/');
            await exerciseAPIRequests.deleteProofExercise(exerciseId!);
            exerciseId = undefined;
        });

        test.afterEach('Clean up if delete failed', async ({ login, exerciseAPIRequests }) => {
            if (exerciseId) {
                await login(admin);
                await exerciseAPIRequests.deleteProofExercise(exerciseId);
                exerciseId = undefined;
            }
        });
    });

    // Seed courses are persistent — no cleanup needed
});
