import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { Course } from 'app/course/shared/entities/course.model';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, tutor } from '../../support/users';
import { generateUUID } from '../../support/utils';

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

        await presentationAssessmentManagement.createPresentation(title, 20);
        await presentationAssessmentManagement.openPresentation(title);

        await presentationAssessmentManagement.assignAndGrade(studentOne.username, dayjs(), 15);
        await expect(presentationAssessmentManagement.instanceRow(studentOne.username)).toContainText('15');

        await presentationAssessmentManagement.changeGrade(studentOne.username, 18);
        await expect(presentationAssessmentManagement.instanceRow(studentOne.username)).toContainText('18');

        await presentationAssessmentManagement.deleteInstance(studentOne.username);
        await expect(presentationAssessmentManagement.instanceRow(studentOne.username)).toHaveCount(0);

        await presentationAssessmentManagement.deletePresentation(title);
        await expect(page.getByRole('link', { name: title })).toHaveCount(0);
    });

    test('Tutor grades a student but cannot manage presentations', async ({ page, login, presentationAssessmentManagement }) => {
        const title = `Tutor presentation ${generateUUID()}`;
        // Still logged in as the admin from beforeEach.
        const created = await page.request.post(`api/assessment/courses/${course.id}/presentation-assessments`, { data: { title, maxPoints: 20, courseId: course.id } });
        expect(created.status()).toBe(201);

        await login(tutor, `/course-management/${course.id}`);
        await page.getByRole('link', { name: 'Presentations' }).click();
        await expect(page.getByTestId('presentation-master-detail')).toBeVisible();
        await expect(page.getByTestId('create-presentation-button')).toHaveCount(0);

        await presentationAssessmentManagement.openPresentation(title);
        await expect(page.getByTestId('edit-presentation-button')).toHaveCount(0);

        await presentationAssessmentManagement.assignAndGrade(studentOne.username, dayjs(), 12);
        const row = presentationAssessmentManagement.instanceRow(studentOne.username);
        await expect(row).toContainText('12');
        await expect(row.getByTestId('edit-instance-button')).toBeVisible();
        await expect(row.getByTestId('delete-instance-button')).toHaveCount(0);
    });
});
