import dayjs from 'dayjs';

import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { expect } from '@playwright/test';

import { ProgrammingExerciseAssessmentType } from '../../../support/constants';
import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import javaPartiallySuccessfulSubmission from '../../../fixtures/exercise/programming/java/partially_successful/submission.json';

const complaint = 'The tests are wrong!';

// A complaint about an automatic result turns the result into the manual result of the first correction round. The tutor opens it
// through the lock endpoint, which used to hand out the submission without any result, so the page showed nothing but the notes box.
test.describe('Programming exercise complaint about an automatic result', { tag: '@slow' }, () => {
    let course: Course;
    let exercise: ProgrammingExercise;
    let assessmentDueDate: dayjs.Dayjs;

    test.beforeEach(
        'Creates an automatically assessed exercise and a student result',
        async ({ login, page, courseManagementAPIRequests, exerciseAPIRequests, waitForParticipationBuildToFinish }) => {
            test.slow();
            await login(admin);
            course = await courseManagementAPIRequests.createCourse();
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            await courseManagementAPIRequests.addTutorToCourse(course, tutor);
            await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
            // The due date has to leave room for the build of the solution and of the student submission
            const dueDate = dayjs().add(4, 'minutes');
            assessmentDueDate = dueDate.add(20, 'seconds');
            exercise = await exerciseAPIRequests.createProgrammingExercise({
                course,
                releaseDate: dayjs(),
                dueDate,
                assessmentDate: assessmentDueDate,
                assessmentType: ProgrammingExerciseAssessmentType.AUTOMATIC,
                allowComplaintsForAutomaticAssessments: true,
            });
            await exerciseAPIRequests.waitForSolutionBuild(exercise.id!);
            await login(studentOne);
            const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
            const participation: Participation = await response.json();
            await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, javaPartiallySuccessfulSubmission);
            await waitForParticipationBuildToFinish(participation.id!);
            const now = dayjs();
            if (now.isBefore(assessmentDueDate)) {
                await page.waitForTimeout(assessmentDueDate.diff(now, 'ms') + 2000);
            }
        },
    );

    test('Shows the code editor and the complaint form when an instructor opens the complaint', async ({ login, page, programmingExerciseFeedback }) => {
        test.slow();
        await login(studentOne, `/courses/${course.id!}/exercises/${exercise.id!}`);
        await programmingExerciseFeedback.complain(complaint);

        await login(instructor, `/course-management/${course.id!}/complaints`);
        const lockResponsePromise = page.waitForResponse((response) => /\/programming-submissions\/\d+\/lock/.test(response.url()));
        await page.locator('tr', { hasText: exercise.title! }).locator('[data-testid="show-complaint"]').click();

        // The submission comes with the result the complaint is about
        const lockResponse = await lockResponsePromise;
        expect(lockResponse.status()).toBe(200);
        expect((await lockResponse.json()).results).toHaveLength(1);

        // The assessment page is complete: the code editor, and the complaint form to answer the complaint with
        await expect(page.locator('[data-testid="programming-toolbar"]')).toBeVisible();
        await expect(page.locator('#responseTextArea')).toBeEnabled();
        await expect(page.locator('#acceptComplaintButton')).toBeVisible();
        await expect(page.locator('#rejectComplaintButton')).toBeVisible();
        await expect(page.locator('[data-testid="assessment-result-not-found"]')).toHaveCount(0);
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
