import { Course } from 'app/entities/course.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

// Common primitives
const tutorFeedback = 'Try to use some newlines next time!';
const tutorFeedbackPoints = 4;
const complaint = "That feedback wasn't very useful!";

test.describe('File upload exercise assessment', () => {
    let course: Course;
    let exercise: FileUploadExercise;

    test.beforeEach(
        'Creates a file upload exercise and makes a student submission',
        async ({ login, courseManagementAPIRequests, exerciseAPIRequests, courseOverview, fileUploadExerciseEditor }) => {
            await login(admin);
            course = await courseManagementAPIRequests.createCourse();
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            await courseManagementAPIRequests.addTutorToCourse(course, tutor);
            await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
            exercise = await exerciseAPIRequests.createFileUploadExercise({ course });
            await login(studentOne, `/courses/${course.id}/exercises`);
            await courseOverview.startExercise(exercise.id!);
            await courseOverview.openRunningExercise(exercise.id!);
            await fileUploadExerciseEditor.attachFile('pdf-test-file.pdf');
            await fileUploadExerciseEditor.submit();
        },
    );

    test('Assesses the file upload exercise submission', async ({ login, courseManagement, courseAssessment, exerciseAssessment, fileUploadExerciseAssessment }) => {
        await login(tutor, '/course-management');
        await courseManagement.openAssessmentDashboardOfCourse(course.id!);
        await courseAssessment.clickExerciseDashboardButton();
        await exerciseAssessment.clickHaveReadInstructionsButton();
        await exerciseAssessment.clickStartNewAssessment();
        await expect(fileUploadExerciseAssessment.getInstructionsRootElement().getByText(exercise.title!)).toBeVisible();
        await expect(fileUploadExerciseAssessment.getInstructionsRootElement().getByText(exercise.problemStatement!)).toBeVisible();
        await expect(fileUploadExerciseAssessment.getInstructionsRootElement().getByText(exercise.exampleSolution!)).toBeVisible();
        await expect(fileUploadExerciseAssessment.getInstructionsRootElement().getByText(exercise.gradingInstructions!)).toBeVisible();
        await fileUploadExerciseAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
        await fileUploadExerciseAssessment.submitFeedback();
    });

    test.describe('Feedback', () => {
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

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
