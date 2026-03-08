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
        // Wait for both template build (SCA categories) and solution build (test cases).
        // Both must complete before the student submits to ensure correct score calculation.
        await exerciseAPIRequests.configureScaCategoriesViaApi(exercise.id!);
        await exerciseAPIRequests.waitForSolutionBuild(exercise.id!);
        // Deactivate LeakSanitizer test — it always fails on ARM64 Docker (macOS).
        // Setting weight=0 excludes it from score calculation.
        await exerciseAPIRequests.deactivateTestCases(exercise.id!, ['TestOutputLSan']);
        // Start student participation and submit code with SCA issues.
        await login(studentOne);
        const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        const participation = await response.json();
        // Create sca_issues.c first — it doesn't exist in the template repo,
        // and the PUT /files endpoint can only update existing files.
        await exerciseAPIRequests.createProgrammingExerciseFile(participation.id!, 'sca_issues.c');
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
