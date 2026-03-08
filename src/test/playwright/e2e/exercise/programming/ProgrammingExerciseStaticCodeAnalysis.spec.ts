import cScaSubmission from '../../../fixtures/exercise/programming/c/static_code_analysis/submission.json';
import { ProgrammingLanguage } from '../../../support/constants';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';
import { Commands } from '../../../support/commands';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

test.describe('Static code analysis tests', { tag: '@slow' }, () => {
    let exercise: ProgrammingExercise;

    test.beforeEach('Create C exercise with SCA, submit via API', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createProgrammingExercise({ course, scaMaxPenalty: 50, programmingLanguage: ProgrammingLanguage.C });
        // Poll until the template build provides SCA categories (~5-10s for C), then set all to GRADED.
        // This MUST happen before the student submits, otherwise the score is calculated without SCA penalties.
        await exerciseAPIRequests.configureScaCategoriesViaApi(exercise.id!);
        // Now submit the student code — the build will use the configured SCA categories.
        await login(studentOne);
        const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        const participation = await response.json();
        await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, cScaSubmission);
    });

    test.afterEach('Delete exercise to prevent DB accumulation', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        await exerciseAPIRequests.deleteProgrammingExercise(exercise.id!);
    });

    test('Verifies SCA feedback is displayed correctly after submission', async ({ login, page, programmingExerciseScaFeedback }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        const resultScore = page.locator('#exercise-headers-information').locator('#result-score');
        const expectedScore = resultScore.getByText(cScaSubmission.expectedResult);
        // C builds complete in 2-5s. Use a 45s timeout to stay within the 90s @slow budget.
        await Commands.reloadUntilFound(page, expectedScore, 5000, 45000);
        await resultScore.click();
        await programmingExerciseScaFeedback.shouldShowPointChart();
        await programmingExerciseScaFeedback.shouldShowCodeIssue("unused variable 'unused_x'", '0.2');
        await programmingExerciseScaFeedback.shouldShowCodeIssue("unused variable 'unused_y'", '0.2');
        await programmingExerciseScaFeedback.closeModal();
    });
});
