import dayjs from 'dayjs';

import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import { ProgrammingExerciseAssessmentType } from '../../../support/constants';
import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { Participation } from 'app/entities/participation/participation.model';
import { expect } from '@playwright/test';

// Common primitives
const tutorFeedback = 'You are missing some classes! The classes, which you implemented look good though.';
const tutorFeedbackPoints = 5;
const tutorCodeFeedback = 'The input parameter should be mentioned in javadoc!';
const tutorCodeFeedbackPoints = -2;
const complaint = "That feedback wasn't very useful!";

test.describe('Programming exercise assessment', () => {
    let course: Course;
    let exercise: ProgrammingExercise;
    let dueDate: dayjs.Dayjs;
    let assessmentDueDate: dayjs.Dayjs;

    test.beforeEach('Creates a programming exercise and makes a student submission', async ({ login, page, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        dueDate = dayjs().add(25, 'seconds');
        assessmentDueDate = dueDate.add(30, 'seconds');
        exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            recordTestwiseCoverage: false,
            releaseDate: dayjs(),
            dueDate: dueDate,
            assessmentDate: assessmentDueDate,
            assessmentType: ProgrammingExerciseAssessmentType.SEMI_AUTOMATIC,
        });
        await login(studentOne);
        const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
        const participation: Participation = await response.json();
        await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!);
        const now = dayjs();
        if (now.isBefore(dueDate)) {
            await page.waitForTimeout(dueDate.diff(now, 'ms'));
        }
    });

    test('Assesses the programming exercise submission and verifies it', async ({
        login,
        page,
        courseManagement,
        courseAssessment,
        exerciseAssessment,
        programmingExerciseEditor,
        programmingExerciseAssessment,
        programmingExerciseFeedback,
        exerciseResult,
    }) => {
        // Asses submission
        await login(tutor, '/course-management');
        await courseManagement.openAssessmentDashboardOfCourse(course.id!);
        await courseAssessment.clickExerciseDashboardButton();
        await exerciseAssessment.clickHaveReadInstructionsButton();
        await exerciseAssessment.clickStartNewAssessment();
        await programmingExerciseEditor.openFileWithName(exercise.id!, 'BubbleSort.java');
        await programmingExerciseAssessment.provideFeedbackOnCodeLine(9, tutorCodeFeedbackPoints, tutorCodeFeedback);
        await programmingExerciseAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
        const assessmentResponse = await programmingExerciseAssessment.submit();
        expect(assessmentResponse.status()).toBe(200);
        // Wait until the assessment due date is over
        const now = dayjs();
        if (now.isBefore(assessmentDueDate)) {
            await page.waitForTimeout(assessmentDueDate.diff(now, 'ms'));
        }

        // Verify assessment as student
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        const totalPoints = tutorFeedbackPoints + tutorCodeFeedbackPoints;
        const percentage = totalPoints * 10;
        await exerciseResult.shouldShowExerciseTitle(exercise.title!);
        await programmingExerciseFeedback.complain(complaint);
        await exerciseResult.clickOpenCodeEditor(exercise.id!);
        await programmingExerciseFeedback.shouldShowRepositoryLockedWarning();
        await programmingExerciseFeedback.shouldShowAdditionalFeedback(tutorFeedbackPoints, tutorFeedback);
        await programmingExerciseFeedback.shouldShowScore(percentage);
        await programmingExerciseFeedback.shouldShowCodeFeedback(exercise.id!, 'BubbleSort.java', tutorCodeFeedback, '-2', programmingExerciseEditor);

        // TODO: Enable after fixing accept/reject complaint
        // Accept complaint
        // await login(instructor, `/course-management/${course.id}/complaints`);
        // const acceptComplaintResponse = await programmingExerciseAssessment.acceptComplaint('Makes sense', false);
        // expect(acceptComplaintResponse.status()).toBe(200);
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
