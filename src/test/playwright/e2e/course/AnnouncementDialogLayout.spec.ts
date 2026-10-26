import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { Course } from 'app/course/shared/entities/course.model';
import { expectNoHorizontalOverflow, expectNoHorizontalScrollAround, expectWithinViewport, forEachViewport } from '../../support/layout';

/** Regression for #12407: short and narrow viewports must keep the entire announcement form reachable. */
test.describe('Announcement dialog layout', { tag: '@fast' }, () => {
    let course: Course | undefined;

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
        course = undefined;
    });

    test('keeps announcement content and actions reachable on small screens', async ({ login, page, courseManagementAPIRequests, communicationAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ courseName: 'Announcement layout regression' });
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        const channel = await communicationAPIRequests.createCourseMessageChannel(course, 'lecture-announcements', 'Lecture announcements', true, true);
        await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, instructor);
        await login(instructor);
        await communicationAPIRequests.acceptCodeOfConduct(course);
        await page.goto(`/courses/${course.id}/communication?conversationId=${channel.id}`);
        await expect(page.getByTestId('conversation-name')).toHaveText(channel.name!);
        await page.getByRole('button', { name: 'New announcement', exact: true }).click();

        const dialog = page.getByRole('dialog');
        const form = page.getByTestId('post-create-edit-form');
        const title = page.getByTestId('post-title');
        const editor = page.getByTestId('formatted-message-input');
        const send = page.getByTestId('send-formatted-message');
        await title.fill('A lecture announcement');
        await editor.fill('The next lecture covers concurrency and synchronization.\n'.repeat(30));

        async function checkLayout() {
            await expectWithinViewport(dialog, ['top', 'right', 'bottom', 'left'], { name: 'announcement dialog' });
            await expectNoHorizontalOverflow(page);
            await expectNoHorizontalScrollAround(form, { name: 'announcement form' });
            await title.scrollIntoViewIfNeeded();
            await expectWithinViewport(title, ['top', 'right', 'bottom', 'left'], { name: 'announcement title' });
            await send.scrollIntoViewIfNeeded();
            await expectWithinViewport(send, ['top', 'right', 'bottom', 'left'], { name: 'announcement submit button' });
            await expect(send).toBeEnabled();
        }

        await forEachViewport(page, checkLayout);
        for (const viewport of [
            { width: 921, height: 535 },
            { width: 480, height: 320 },
        ]) {
            await page.setViewportSize(viewport);
            await checkLayout();
        }
        await page.screenshot({ path: test.info().outputPath('announcement-short-viewport.png'), fullPage: true });
        await send.click();
        await expect(dialog).not.toBeVisible();
        await expect(page.getByText('A lecture announcement', { exact: true })).toBeVisible();
    });
});
