import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { Course } from 'app/course/shared/entities/course.model';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, tutor } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { PresentationGradingMethod } from '../../support/constants';

test.describe('Presentation assessments', { tag: '@fast' }, () => {
    let course: Course;

    test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ presentationAssessmentsEnabled: true });
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('Instructor creates a presentation, grades a student and deletes both again', async ({ page, login, presentationAssessmentManagement }) => {
        const title = `Final presentation ${generateUUID()}`;
        await login(instructor);
        await presentationAssessmentManagement.goto(course.id!);

        await presentationAssessmentManagement.createPresentation(title, PresentationGradingMethod.SCORE, { maxPoints: 20 });
        await presentationAssessmentManagement.openPresentation(title);

        await presentationAssessmentManagement.assignAndGrade(studentOne.username, dayjs(), 15);
        await expect(presentationAssessmentManagement.instanceRow(studentOne.username).getByTestId('instance-result')).toHaveText('15 / 20');

        await presentationAssessmentManagement.changeGrade(studentOne.username, 18);
        await expect(presentationAssessmentManagement.instanceRow(studentOne.username).getByTestId('instance-result')).toHaveText('18 / 20');

        await presentationAssessmentManagement.deleteInstance(studentOne.username);
        await expect(presentationAssessmentManagement.instanceRow(studentOne.username)).toHaveCount(0);

        await presentationAssessmentManagement.deletePresentation(title);
        await expect(page.getByRole('link', { name: title })).toHaveCount(0);

        // Reload so that the list comes from the server, not from the client state.
        await presentationAssessmentManagement.goto(course.id!);
        await expect(page.getByRole('link', { name: title })).toHaveCount(0);
    });

    test('Tutor grades a student but cannot manage presentations', async ({ page, login, presentationAssessmentManagement }) => {
        const title = `Tutor presentation ${generateUUID()}`;
        // Still logged in as the admin from beforeEach.
        const created = await page.request.post(`api/assessment/courses/${course.id}/presentation-assessments`, {
            data: { title, gradingMethod: PresentationGradingMethod.PASS_FAIL, courseId: course.id },
        });
        expect(created.status()).toBe(201);

        await login(tutor, `/course-management/${course.id}`);
        await page.locator('#presentations').click();
        await expect(page.getByTestId('presentation-master-detail')).toBeVisible();
        await expect(page.getByTestId('create-presentation-button')).toHaveCount(0);

        await presentationAssessmentManagement.openPresentation(title);
        await expect(page.getByTestId('edit-presentation-button')).toHaveCount(0);

        await presentationAssessmentManagement.assignAndGradeByPassFail(studentOne.username, dayjs(), false);
        const row = presentationAssessmentManagement.instanceRow(studentOne.username);
        await expect(row.getByTestId('instance-result')).toHaveText('Failed');
        await expect(row.getByTestId('edit-instance-button')).toBeVisible();
        await expect(row.getByTestId('delete-instance-button')).toHaveCount(0);
    });

    test('Instructor creates a presentation graded by rubric and grades a student with it', async ({ page, login, presentationAssessmentManagement }) => {
        const title = `Rubric presentation ${generateUUID()}`;
        await login(instructor);
        await presentationAssessmentManagement.goto(course.id!);

        await presentationAssessmentManagement.createPresentation(title, PresentationGradingMethod.RUBRIC, {
            criteria: [
                { name: 'Content', weight: 60 },
                { name: 'Delivery', weight: 40 },
            ],
        });
        await presentationAssessmentManagement.openPresentation(title);
        await expect(page.getByTestId('edit-rubric-button')).toBeVisible();

        // Both criteria have five levels: 60 % · 3/4 for "Good" and 40 % · 4/4 for "Excellent"
        await presentationAssessmentManagement.assignAndGradeByRubric(studentOne.username, dayjs(), [3, 4]);

        await expect(presentationAssessmentManagement.instanceRow(studentOne.username).getByTestId('instance-result')).toHaveText('85 %');
    });
});
