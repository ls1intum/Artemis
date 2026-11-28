import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { Course } from 'app/course/shared/entities/course.model';
import { test } from '../../support/fixtures';
import { admin, studentOne, tutor } from '../../support/users';

test.describe('Tutorial group sidebar attendance chip', { tag: '@fast' }, () => {
    let course: Course | undefined;

    test.beforeEach(async ({ login, page, courseManagementAPIRequests, userManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ timeZone: 'Europe/Berlin' });
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.createTutorialGroupsConfiguration(course);
        const tutorResponse = await userManagementAPIRequests.getUser(tutor.username);
        expect(tutorResponse.ok()).toBeTruthy();
        // The group has a capacity of 10, so one attendee in the only past session is an average attendance of 10 %
        const tutorialGroupId = await courseManagementAPIRequests.createTutorialGroup(course, 'Attendance Chip', (await tutorResponse.json()).id);
        await courseManagementAPIRequests.registerStudentsInTutorialGroup(course, tutorialGroupId, [studentOne]);
        const session = await page.request.post(`api/tutorialgroup/courses/${course.id}/tutorial-groups/${tutorialGroupId}/sessions`, {
            data: { date: dayjs().subtract(2, 'day').format('YYYY-MM-DD'), startTime: '10:00:00', endTime: '11:00:00', location: 'Room 1', attendance: 1 },
        });
        expect(session.ok(), await session.text()).toBeTruthy();
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
        course = undefined;
    });

    // Students never receive the number of registered users, so the average attendance sent by the server is all they have.
    test('shows the average attendance of a group to a student', async ({ login, page }) => {
        await login(studentOne, `/courses/${course!.id}/tutorial-groups`);
        await expect(page.getByTestId('sidebar-attendance-chip')).toHaveText('Ø 10%');
    });

    test('shows the average attendance of a group to the tutor', async ({ login, page }) => {
        await login(tutor, `/courses/${course!.id}/tutorial-groups`);
        await expect(page.getByTestId('sidebar-attendance-chip')).toHaveText('Ø 10%');
    });
});
