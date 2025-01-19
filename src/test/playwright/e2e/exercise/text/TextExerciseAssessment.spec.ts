import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import dayjs from 'dayjs/esm';

import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { Fixtures } from '../../../fixtures/fixtures';
import { expect } from '@playwright/test';
import { Commands } from '../../../support/commands';
import { CourseManagementAPIRequests } from '../../../support/requests/CourseManagementAPIRequests';
import { ExerciseAPIRequests } from '../../../support/requests/ExerciseAPIRequests';

// Common primitives
const tutorFeedback = 'Try to use some newlines next time!';
const tutorFeedbackPoints = 4;
const tutorTextFeedback = 'Nice ending of the sentence!';
const tutorTextFeedbackPoints = 2;
const complaint = "That feedback wasn't very useful!";

test.describe('Text exercise assessment', { tag: '@fast' }, () => {
    let course: Course;
    let exercise: TextExercise;
    let dueDate: dayjs.Dayjs;
    let assessmentDueDate: dayjs.Dayjs;
    test.beforeAll('Create course and make a submission', async ({ browser }) => {
        const context = await browser.newContext();
        const page = await context.newPage();
        dueDate = dayjs().add(15, 'seconds');
        assessmentDueDate = dueDate.add(20, 'seconds');
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);
        await Commands.login(page, admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        exercise = await exerciseAPIRequests.createTextExerciseWithDates({ course }, dayjs(), dueDate, assessmentDueDate);
        await Commands.login(page, studentOne);
        await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        const submission = await Fixtures.get('loremIpsum-short.txt');
        await exerciseAPIRequests.makeTextExerciseSubmission(exercise.id!, submission!);
        //exercise = await exerciseAPIRequests.createTextExercise({ course });
        const now = dayjs();
        if (now.isBefore(dueDate)) {
            await page.waitForTimeout(dueDate.diff(now, 'ms'));
        }
    });

    test.describe.serial('Feedback', () => {
        test('Assesses the text exercise submission', async ({ login, courseManagement, courseAssessment, exerciseAssessment, textExerciseAssessment }) => {
            await login(tutor, '/course-management');
            await courseManagement.openAssessmentDashboardOfCourse(course.id!);
            await courseAssessment.clickExerciseDashboardButton();
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

        test('Student sees feedback after assessment due date and complains', async ({ login, page, exerciseResult, textExerciseFeedback }) => {
            const now = dayjs();
            if (now.isBefore(assessmentDueDate)) {
                await page.waitForTimeout(assessmentDueDate.diff(now, 'ms'));
            }
            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            const totalPoints = tutorFeedbackPoints + tutorTextFeedbackPoints;
            const percentage = totalPoints * 10;
            await exerciseResult.shouldShowExerciseTitle(exercise.title!);
            await exerciseResult.shouldShowProblemStatement(exercise.problemStatement!);
            await exerciseResult.shouldShowScore(percentage);
            await exerciseResult.clickOpenExercise(exercise.id!);
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

    test.afterAll('Delete course', async ({ browser }) => {
        const context = await browser.newContext();
        const page = await context.newPage();
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
