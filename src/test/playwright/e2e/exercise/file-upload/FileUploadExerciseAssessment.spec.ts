import { Course } from 'app/entities/course.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { Commands } from '../../../support/commands';
import { CourseManagementAPIRequests } from '../../../support/requests/CourseManagementAPIRequests';
import { ExerciseAPIRequests } from '../../../support/requests/ExerciseAPIRequests';
import { CourseOverviewPage } from '../../../support/pageobjects/course/CourseOverviewPage';
import { FileUploadEditorPage } from '../../../support/pageobjects/exercises/file-upload/FileUploadEditorPage';
import { newBrowserPage } from '../../../support/utils';

// Common primitives
const tutorFeedback = 'Try to use some newlines next time!';
const tutorFeedbackPoints = 4;
const complaint = "That feedback wasn't very useful!";

test.describe('File upload exercise assessment', () => {
    let course: Course;
    let exercise: FileUploadExercise;

    test.beforeAll('Creates a file upload exercise and makes a student submission', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);
        const courseOverview = new CourseOverviewPage(page);
        const fileUploadExerciseEditor = new FileUploadEditorPage(page);

        await Commands.login(page, admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        exercise = await exerciseAPIRequests.createFileUploadExercise({ course });
        await Commands.login(page, studentOne, `/courses/${course.id}/exercises`);
        await courseOverview.startExercise(exercise.id!);
        await courseOverview.openRunningExercise(exercise.id!);
        await fileUploadExerciseEditor.attachFile('pdf-test-file.pdf');
        await fileUploadExerciseEditor.submit();
    });

    test.describe.serial('Feedback', () => {
        test('Assesses the file upload exercise submission', async ({ login, courseManagement, courseAssessment, exerciseAssessment, fileUploadExerciseAssessment }) => {
            await login(tutor, '/course-management');
            await courseManagement.openAssessmentDashboardOfCourse(course.id!);
            await courseAssessment.clickExerciseDashboardButton();
            await exerciseAssessment.clickHaveReadInstructionsButton();
            await exerciseAssessment.clickStartNewAssessment();
            await expect(fileUploadExerciseAssessment.getInstructionsRootElement().getByText(exercise.title!)).toBeVisible();
            await expect(fileUploadExerciseAssessment.getInstructionsRootElement().locator('.collapse.show').getByText(exercise.problemStatement!)).toBeVisible();
            await expect(fileUploadExerciseAssessment.getInstructionsRootElement().locator('.collapse.show').getByText(exercise.exampleSolution!)).toBeVisible();
            await expect(fileUploadExerciseAssessment.getInstructionsRootElement().locator('.collapse.show').getByText(exercise.gradingInstructions!)).toBeVisible();
            await fileUploadExerciseAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
            await fileUploadExerciseAssessment.submitFeedback();
        });

        test('Student sees feedback after assessment due date and complains', async ({ login, exerciseResult, fileUploadExerciseFeedback }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            const percentage = tutorFeedbackPoints * 10;
            await exerciseResult.shouldShowExerciseTitle(exercise.title!);
            await exerciseResult.shouldShowProblemStatement(exercise.problemStatement!);
            await exerciseResult.shouldShowScore(percentage);
            await exerciseResult.clickOpenExercise(exercise.id!);
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

    test.afterAll('Delete course', async ({ browser }) => {
        const context = await browser.newContext();
        const page = await context.newPage();
        const courseManagementAPIRequests = new CourseManagementAPIRequests(page);
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
