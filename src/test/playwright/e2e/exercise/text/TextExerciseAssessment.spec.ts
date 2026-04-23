import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import dayjs from 'dayjs';

import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { Fixtures } from '../../../fixtures/fixtures';
import { expect } from '@playwright/test';
import { Commands } from '../../../support/commands';
import { ExerciseAPIRequests } from '../../../support/requests/ExerciseAPIRequests';
import { SEED_COURSES } from '../../../support/seedData';

// Common primitives
const tutorFeedback = 'Try to use some newlines next time!';
const tutorFeedbackPoints = 4;
const tutorTextFeedback = 'Nice ending of the sentence!';
const tutorTextFeedbackPoints = 2;
const complaint = "That feedback wasn't very useful!";

const course = { id: SEED_COURSES.textAssessment.id } as any;

test.describe('Text exercise assessment', { tag: '@slow' }, () => {
    let exercise: TextExercise;
    let dueDate: dayjs.Dayjs;
    let assessmentDueDate: dayjs.Dayjs;
    test.beforeAll('Create exercise and make a submission', async ({ browser }) => {
        const context = await browser.newContext({ ignoreHTTPSErrors: true });
        const page = await context.newPage();
        const exerciseAPIRequests = new ExerciseAPIRequests(page);
        await Commands.login(page, admin);
        // Initialize deadlines after login so the short windows aren't consumed by setup.
        // Use generous windows to avoid flakiness under CI parallel load.
        dueDate = dayjs().add(10, 'seconds');
        assessmentDueDate = dueDate.add(10, 'seconds');
        exercise = await exerciseAPIRequests.createTextExerciseWithDates({ course }, dayjs(), dueDate, assessmentDueDate);
        await Commands.login(page, studentOne);
        await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        const submission = await Fixtures.get('loremIpsum-short.txt');
        await exerciseAPIRequests.makeTextExerciseSubmission(exercise.id!, submission!);
        const now = dayjs();
        if (now.isBefore(dueDate)) {
            await page.waitForTimeout(dueDate.diff(now, 'ms') + 2000);
        }
    });

    test.describe.serial('Feedback', () => {
        test('Assesses the text exercise submission', async ({ login, page, exerciseAssessment, textExerciseAssessment }) => {
            await login(tutor, `/course-management/${course.id}/assessment-dashboard/${exercise.id!}`);
            await exerciseAssessment.clickHaveReadInstructionsButton();
            await exerciseAssessment.clickStartNewAssessment();
            await expect(textExerciseAssessment.getInstructionsRootElement().filter({ hasText: exercise.title })).toBeVisible();
            await expect(textExerciseAssessment.getInstructionsRootElement().filter({ hasText: exercise.problemStatement! })).toBeVisible();
            await expect(textExerciseAssessment.getInstructionsRootElement().filter({ hasText: exercise.exampleSolution! })).toBeVisible();
            await expect(textExerciseAssessment.getInstructionsRootElement().filter({ hasText: exercise.gradingInstructions! })).toBeVisible();
            // Assert the correct word and character count without relying on translations
            await expect(textExerciseAssessment.getWordCountElement().filter({ hasText: '16' })).toBeVisible();
            await expect(textExerciseAssessment.getCharacterCountElement().filter({ hasText: '83' })).toBeVisible();
            await textExerciseAssessment.provideFeedbackOnTextSection(1, tutorTextFeedbackPoints, tutorTextFeedback);
            await textExerciseAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
            const response = await textExerciseAssessment.submit();
            expect(response.status()).toBe(200);
        });

        test('Student sees feedback after assessment due date and complains', async ({ login, page, courseManagementAPIRequests, exerciseResult, textExerciseFeedback }) => {
            const now = dayjs();
            if (now.isBefore(assessmentDueDate)) {
                await page.waitForTimeout(assessmentDueDate.diff(now, 'ms') + 2000);
            }
            // Reset complaint limit to avoid "complaint limit reached" on shared seed courses
            await login(admin);
            await courseManagementAPIRequests.updateCourseMaxComplaints(course.id, 999);
            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            const totalPoints = tutorFeedbackPoints + tutorTextFeedbackPoints;
            const percentage = totalPoints * 10;
            await exerciseResult.shouldShowExerciseTitle(exercise.title!);
            await exerciseResult.shouldShowProblemStatement(exercise.problemStatement!);
            await exerciseResult.shouldShowScore(percentage);
            await textExerciseFeedback.shouldShowTextFeedback(1, tutorTextFeedback);
            await textExerciseFeedback.shouldShowAdditionalFeedback(tutorFeedbackPoints, tutorFeedback);
            await textExerciseFeedback.shouldShowScore(percentage);
            await textExerciseFeedback.complain(complaint);
        });

        test('Instructor can see complaint and reject it', async ({ login, textExerciseAssessment }) => {
            await login(instructor, `/course-management/${course.id}/complaints`);
            const response = await textExerciseAssessment.acceptComplaint('Makes sense', false);
            expect(response.status()).toBe(200);
        });
    });

    // Seed courses are persistent — no cleanup needed
});
