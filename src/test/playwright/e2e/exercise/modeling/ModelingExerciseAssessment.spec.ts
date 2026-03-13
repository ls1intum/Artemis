import dayjs from 'dayjs';

import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';

import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { ExerciseAPIRequests } from '../../../support/requests/ExerciseAPIRequests';
import { Commands } from '../../../support/commands';
import { newBrowserPage } from '../../../support/utils';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseAssessment.id } as any;

test.describe('Modeling Exercise Assessment', { tag: '@slow' }, () => {
    let modelingExercise: ModelingExercise;

    test.beforeAll('Create course and make a submission', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);

        await Commands.login(page, admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
        await Commands.login(page, studentOne);
        const response = await exerciseAPIRequests.startExerciseParticipation(modelingExercise.id!);
        const participation = await response.json();
        await exerciseAPIRequests.makeModelingExerciseSubmission(modelingExercise.id!, participation);
        await Commands.login(page, instructor);
        // Use current time (not past) to ensure submissionDate < dueDate for rated result
        // The dueDate will be in the past by the time the next test runs
        await exerciseAPIRequests.updateModelingExerciseDueDate(modelingExercise, dayjs());
    });

    test.describe.serial('Handling complaints', () => {
        test('Tutor can assess a submission', async ({ login, courseManagement, exerciseAssessment, modelingExerciseAssessment, toggleSidebar }) => {
            await login(tutor, '/course-management');
            await courseManagement.openSubmissionsForExerciseAndCourse(course.id!, modelingExercise.id!);
            await toggleSidebar();
            await courseManagement.checkIfStudentSubmissionExists(studentOne.username);
            await login(tutor, `/course-management/${course.id}/assessment-dashboard/${modelingExercise.id!}`);
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

        test('Student can view the assessment and complain', async ({ login, exerciseAPIRequests, courseManagementAPIRequests, exerciseResult, modelingExerciseFeedback }) => {
            await login(admin);
            const response = await exerciseAPIRequests.updateModelingExerciseAssessmentDueDate(modelingExercise, dayjs());
            modelingExercise = await response.json();
            // Reset complaint limit to avoid "complaint limit reached" on shared seed courses
            await courseManagementAPIRequests.updateCourseMaxComplaints(course.id, 999);
            await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
            await exerciseResult.shouldShowExerciseTitle(modelingExercise.title!);
            await exerciseResult.shouldShowScore(20);
            await exerciseResult.clickOpenExerciseAndAwaitRatingResponse(modelingExercise.id!);
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

    // Seed courses are persistent — no cleanup needed
});
