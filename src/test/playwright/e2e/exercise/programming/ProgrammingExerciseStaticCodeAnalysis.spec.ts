import cScaSubmission from '../../../fixtures/exercise/programming/c/static_code_analysis/submission.json';
import { ProgrammingLanguage } from '../../../support/constants';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';
import { Commands } from '../../../support/commands';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

test.describe('Static code analysis tests', { tag: '@fast' }, () => {
    let exercise: ProgrammingExercise;

    test.beforeEach('Create C exercise with SCA, submit via API', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createProgrammingExercise({ course, scaMaxPenalty: 50, programmingLanguage: ProgrammingLanguage.C });
        await login(studentOne);
        const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        const participation = await response.json();
        await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, cScaSubmission);
        await login(admin);
        await exerciseAPIRequests.configureScaCategoriesViaApi(exercise.id!);
    });

    test('Verifies SCA feedback is displayed correctly after submission', async ({ login, page, programmingExerciseScaFeedback }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        const resultScore = page.locator('#exercise-headers-information').locator('#result-score');
        const expectedScore = resultScore.getByText(cScaSubmission.expectedResult);
        await Commands.reloadUntilFound(page, expectedScore, 5000, 60000);
        await resultScore.click();
        await programmingExerciseScaFeedback.shouldShowPointChart();
        await programmingExerciseScaFeedback.shouldShowCodeIssue("unused variable 'unused_x'", '0.2');
        await programmingExerciseScaFeedback.shouldShowCodeIssue("unused variable 'unused_y'", '0.2');
        await programmingExerciseScaFeedback.closeModal();
    });
});
