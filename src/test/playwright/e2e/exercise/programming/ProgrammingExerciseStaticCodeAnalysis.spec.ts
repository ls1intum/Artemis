import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import javaScaSubmission from '../../../fixtures/exercise/programming/java/static_code_analysis/submission.json';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';
import { Commands } from '../../../support/commands';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

test.describe('Static code analysis tests', { tag: '@sequential' }, () => {
    let exercise: ProgrammingExercise;

    test.beforeEach('Create exercise, submit via API, then configure SCA', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createProgrammingExercise({ course, scaMaxPenalty: 50 });
        // Start participation and submit BEFORE configuring SCA categories.
        // This queues the student build early so it processes during the SCA category wait.
        // SCA categories only affect how feedback is displayed, not the build itself.
        await login(studentOne);
        const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        const participation = await response.json();
        for (const file of javaScaSubmission.files) {
            const packagePath = javaScaSubmission.packageName.replace(/\./g, '/');
            const filename = `src/${packagePath}/${file.name}`;
            if (!javaScaSubmission.deleteFiles.includes(file.name)) {
                await exerciseAPIRequests.createProgrammingExerciseFile(participation.id!, filename);
            }
        }
        await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, javaScaSubmission);
        // Configure SCA categories (polls until template build provides them, ~30-45s).
        // During this wait, the solution and student builds are also processing.
        await login(admin);
        await exerciseAPIRequests.configureScaCategoriesViaApi(exercise.id!);
    });

    test('Verifies SCA feedback is displayed correctly after submission', async ({ login, page, programmingExerciseScaFeedback }) => {
        // Java builds are inherently slow (30-60s each) and the SCA test requires Java.
        // With build agent contention from parallel tests, 90s is not enough.
        test.setTimeout(150000);
        // Navigate to the exercise result — the Java build may still be in progress,
        // so poll with page reloads until the result score appears.
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        const resultScore = page.locator('#exercise-headers-information').locator('#result-score');
        await Commands.reloadUntilFound(page, resultScore, 5000, 120000);
        await expect(resultScore.getByText(javaScaSubmission.expectedResult)).toBeVisible({ timeout: 10000 });
        await resultScore.click();
        await programmingExerciseScaFeedback.shouldShowPointChart();
        await programmingExerciseScaFeedback.shouldShowCodeIssue("Variable 'literal1' must be private and have accessor methods.", '5');
        await programmingExerciseScaFeedback.shouldShowCodeIssue("Avoid unused private fields such as 'LITERAL_TWO'.", '0.5');
        await programmingExerciseScaFeedback.shouldShowCodeIssue("de.test.BubbleSort.literal1 isn't final but should be", '2.5');
        await programmingExerciseScaFeedback.shouldShowCodeIssue('Unread public/protected field: de.test.BubbleSort.literal1', '0.2');
        await programmingExerciseScaFeedback.closeModal();
    });

    // Seed courses are persistent — no cleanup needed
});
