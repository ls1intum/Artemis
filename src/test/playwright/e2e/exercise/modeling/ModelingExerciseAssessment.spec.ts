import dayjs from 'dayjs';

import { Course } from 'app/entities/course.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

test.describe('Modeling Exercise Assessment', () => {
    let course: Course;
    let modelingExercise: ModelingExercise;

    test.beforeEach('Create course and make submission', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
        await login(studentOne);
        // await page.waitForTimeout(500);
        const response = await exerciseAPIRequests.startExerciseParticipation(modelingExercise.id!);
        const participation = await response.json();
        await exerciseAPIRequests.makeModelingExerciseSubmission(modelingExercise.id!, participation);
        await login(instructor);
        await exerciseAPIRequests.updateModelingExerciseDueDate(modelingExercise, dayjs().add(5, 'seconds'));
    });

    test('Tutor can assess a submission', async ({ login, courseManagement, courseAssessment, exerciseAssessment, modelingExerciseAssessment }) => {
        await login(tutor, '/course-management');
        await courseManagement.openAssessmentDashboardOfCourse(course.id!);
        // await page.waitForTimeout(500);
        await courseAssessment.clickExerciseDashboardButton();
        await exerciseAssessment.clickHaveReadInstructionsButton();
        await exerciseAssessment.clickStartNewAssessment();
        await expect(exerciseAssessment.getLockedMessage()).toBeVisible();
        await modelingExerciseAssessment.addNewFeedback(1, 'Thanks, good job.');
        await modelingExerciseAssessment.openAssessmentForComponent(1);
        await modelingExerciseAssessment.assessComponent(-1, 'False');
        await modelingExerciseAssessment.clickNextAssessment();
        await modelingExerciseAssessment.assessComponent(2, 'Good');
        await modelingExerciseAssessment.clickNextAssessment();
        await modelingExerciseAssessment.assessComponent(0, 'Unnecessary');
        await modelingExerciseAssessment.submit();
    });

    test.describe.serial('Handling complaints', () => {
        test.beforeEach('Update assessment due date', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            const response = await exerciseAPIRequests.updateModelingExerciseAssessmentDueDate(modelingExercise, dayjs());
            modelingExercise = await response.json();
        });

        test('Student can view the assessment and complain', async ({ login, exerciseResult, modelingExerciseFeedback }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
            await exerciseResult.shouldShowExerciseTitle(modelingExercise.title!);
            await exerciseResult.shouldShowScore(20);
            await exerciseResult.clickOpenExercise(modelingExercise.id!);
            await modelingExerciseFeedback.shouldShowScore(20);
            await modelingExerciseFeedback.shouldShowAdditionalFeedback(1, 'Thanks, good job.');
            await modelingExerciseFeedback.shouldShowComponentFeedback(1, 2, 'Good');
            await modelingExerciseFeedback.complain('I am not happy with your assessment.');
        });

        test('Instructor can see complaint and reject it', async ({ login, courseAssessment, modelingExerciseAssessment }) => {
            await login(instructor, `/course-management/${course.id}/complaints`);
            await courseAssessment.showTheComplaint();
            await modelingExerciseAssessment.rejectComplaint('You are wrong.', false);
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
