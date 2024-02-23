import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import javaScaSubmission from '../../../fixtures/exercise/programming/java/static_code_analysis/submission.json';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

test.describe('Static code analysis tests', () => {
    let course: Course;
    let exercise: ProgrammingExercise;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        exercise = await exerciseAPIRequests.createProgrammingExercise({ course, scaMaxPenalty: 50 });
    });

    test('Configures SCA grading and makes a successful submission with SCA errors', async ({
        login,
        programmingExercisesScaConfig,
        programmingExerciseEditor,
        programmingExerciseScaFeedback,
    }) => {
        // Configure SCA grading
        await login(admin);
        await programmingExercisesScaConfig.visit(course.id!, exercise.id!);
        await programmingExercisesScaConfig.makeEveryScaCategoryInfluenceGrading();
        await programmingExercisesScaConfig.saveChanges();

        // Make submission with SCA errors
        await programmingExerciseEditor.startParticipation(course.id!, exercise.id!, studentOne);
        await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, javaScaSubmission, async () => {
            const resultScore = await programmingExerciseEditor.getResultScore();
            await expect(resultScore.getByText(javaScaSubmission.expectedResult)).toBeVisible();
            await resultScore.click();
            await programmingExerciseScaFeedback.shouldShowPointChart();
            // We have to verify those static texts here. If we don't verify those messages the only difference between the SCA and normal programming exercise
            // tests is the score, which hardly verifies the SCA functionality
            await programmingExerciseScaFeedback.shouldShowCodeIssue("Variable 'literal1' must be private and have accessor methods.", '5');
            await programmingExerciseScaFeedback.shouldShowCodeIssue("Avoid unused private fields such as 'LITERAL_TWO'.", '0.5');
            await programmingExerciseScaFeedback.shouldShowCodeIssue("de.test.BubbleSort.literal1 isn't final but should be", '2.5');
            await programmingExerciseScaFeedback.shouldShowCodeIssue('Unread public/protected field: de.test.BubbleSort.literal1', '0.2');
            await programmingExerciseScaFeedback.closeModal();
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
