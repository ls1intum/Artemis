import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { Course } from 'app/course/shared/entities/course.model';
import { test } from '../../support/fixtures';
import { admin, studentOne, tutor } from '../../support/users';
import { expectInside } from '../../support/layout';

test.describe('Tutorial group detail layout', { tag: '@fast' }, () => {
    let course: Course | undefined;
    let tutorialGroupId: number;

    test.beforeEach(async ({ login, page, courseManagementAPIRequests, userManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ timeZone: 'Europe/Berlin' });
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.createTutorialGroupsConfiguration(course);
        const tutorResponse = await userManagementAPIRequests.getUser(tutor.username);
        expect(tutorResponse.ok()).toBeTruthy();
        tutorialGroupId = await courseManagementAPIRequests.createTutorialGroup(course, 'Layout Group', (await tutorResponse.json()).id);
        await courseManagementAPIRequests.registerStudentsInTutorialGroup(course, tutorialGroupId, [studentOne]);
        // The attendance of a past session is what the chart of the average attendance shows
        const session = await page.request.post(`api/tutorialgroup/courses/${course.id}/tutorial-groups/${tutorialGroupId}/sessions`, {
            data: { date: dayjs().subtract(2, 'day').format('YYYY-MM-DD'), startTime: '10:00:00', endTime: '11:00:00', location: 'Room 1', attendance: 1 },
        });
        expect(session.ok(), await session.text()).toBeTruthy();
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
        course = undefined;
    });

    test('keeps the chart of the average attendance inside of its card', async ({ login, page }) => {
        await login(tutor, `/courses/${course!.id}/tutorial-groups/${tutorialGroupId}`);
        const card = page.getByTestId('tutorial-group-attendance-card');
        const chart = page.getByTestId('tutorial-group-attendance-chart');
        await expect(chart).toBeVisible();
        // The card has to grow with its content, or the chart ends below it
        await expectInside(chart, card, ['top', 'bottom'], { name: 'Chart of the average attendance' });
    });
});
