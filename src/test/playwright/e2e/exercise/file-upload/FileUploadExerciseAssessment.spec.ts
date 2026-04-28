import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import dayjs from 'dayjs';

import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { Commands } from '../../../support/commands';
import { ExerciseAPIRequests } from '../../../support/requests/ExerciseAPIRequests';
import { CourseOverviewPage } from '../../../support/pageobjects/course/CourseOverviewPage';
import { FileUploadEditorPage } from '../../../support/pageobjects/exercises/file-upload/FileUploadEditorPage';
import { newBrowserPage } from '../../../support/utils';
import { SEED_COURSES } from '../../../support/seedData';

// Common primitives
const tutorFeedback = 'Try to use some newlines next time!';
const tutorFeedbackPoints = 4;
const complaint = "That feedback wasn't very useful!";

const course = { id: SEED_COURSES.exerciseAssessment.id } as any;

test.describe('File upload exercise assessment', { tag: '@slow' }, () => {
    let exercise: FileUploadExercise;

    test.beforeAll('Creates a file upload exercise and makes a student submission', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);
        const courseOverview = new CourseOverviewPage(page);
        const fileUploadExerciseEditor = new FileUploadEditorPage(page);

        await Commands.login(page, admin);
        exercise = await exerciseAPIRequests.createFileUploadExercise({ course });
        await Commands.login(page, studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        await courseOverview.startExercise(exercise.id!);
        await fileUploadExerciseEditor.attachFile('pdf-test-file.pdf');
        await courseOverview.submitExercise('api/fileupload/exercises/*/file-upload-submissions');
    });

    test.describe.serial('Feedback', () => {
        test('Assesses the file upload exercise submission', async ({ login, exerciseAssessment, fileUploadExerciseAssessment }) => {
            await login(tutor, `/course-management/${course.id}/assessment-dashboard/${exercise.id!}`);
            await exerciseAssessment.clickHaveReadInstructionsButton();
            await exerciseAssessment.clickStartNewAssessment();
            await expect(fileUploadExerciseAssessment.getInstructionsRootElement().getByText(exercise.title!)).toBeVisible();
            await expect(fileUploadExerciseAssessment.getInstructionsRootElement().locator('.collapse.show').getByText(exercise.problemStatement!)).toBeVisible();
            await expect(fileUploadExerciseAssessment.getInstructionsRootElement().locator('.collapse.show').getByText(exercise.exampleSolution!)).toBeVisible();
            await expect(fileUploadExerciseAssessment.getInstructionsRootElement().locator('.collapse.show').getByText(exercise.gradingInstructions!)).toBeVisible();
            await fileUploadExerciseAssessment.downloadSubmissionFile();
            await fileUploadExerciseAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
            await fileUploadExerciseAssessment.submitFeedback();
        });

        test('Student sees feedback after assessment due date and complains', async ({
            login,
            exerciseAPIRequests,
            courseManagementAPIRequests,
            exerciseResult,
            fileUploadExerciseFeedback,
        }) => {
            // Ensure assessment due date is in the past so complaints are allowed
            await login(admin);
            await exerciseAPIRequests.updateFileUploadExerciseAssessmentDueDate(exercise, dayjs());
            // Reset complaint limit to avoid "complaint limit reached" on shared seed courses
            await courseManagementAPIRequests.updateCourseMaxComplaints(course.id, 999);
            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            const percentage = tutorFeedbackPoints * 10;
            await exerciseResult.shouldShowExerciseTitle(exercise.title!);
            await exerciseResult.shouldShowProblemStatement(exercise.problemStatement!);
            await exerciseResult.shouldShowScore(percentage);
            await fileUploadExerciseFeedback.shouldShowAdditionalFeedback(tutorFeedbackPoints, tutorFeedback);
            await fileUploadExerciseFeedback.shouldShowScore(percentage);
            await fileUploadExerciseFeedback.complain(complaint);
        });

        test('Instructor can see complaint and reject it', async ({ login, fileUploadExerciseAssessment }) => {
            await login(instructor, `/course-management/${course.id}/complaints`);
            const response = await fileUploadExerciseAssessment.acceptComplaint('Makes sense', false);
            expect(response.status()).toBe(200);
        });
    });

    // Seed courses are persistent — no cleanup needed
});
