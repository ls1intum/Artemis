import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { expectWithinViewport, forEachViewport } from '../../support/layout';
import { generateUUID } from '../../support/utils';
import { trackWebsocketSubscriptions } from '../../support/websocketSubscriptions';

test('Conversation notifications leave Send accessible, preserve the draft and open the reply', async ({
    browser,
    baseURL,
    login,
    courseManagementAPIRequests,
    communicationAPIRequests,
    page,
}, testInfo) => {
    await login(admin);
    const course = await courseManagementAPIRequests.createCourse();
    const studentContext = await browser.newContext({ baseURL, serviceWorkers: 'block', viewport: { width: 1280, height: 800 } });
    try {
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        const suffix = generateUUID().slice(0, 8);
        const currentChannel = await communicationAPIRequests.createCourseMessageChannel(course, `popup-current-${suffix}`, 'Current conversation', false, true);
        const otherChannel = await communicationAPIRequests.createCourseMessageChannel(course, `popup-other-${suffix}`, 'Other conversation', false, true);
        for (const channel of [currentChannel, otherChannel]) {
            expect(channel.id).toBeDefined();
            await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, studentOne);
        }

        // Use a fresh context: the shared fixture intentionally hides notification popups.
        // Suppress only the unrelated passkey reminder here.
        await studentContext.addInitScript(() => localStorage.setItem('earliestSetupPasskeyReminderDate', JSON.stringify('2099-01-01T00:00:00.000Z')));
        const authentication = await studentContext.request.post('/api/core/public/authenticate', {
            data: { username: studentOne.username, password: studentOne.password, rememberMe: true },
        });
        expect(authentication.ok()).toBe(true);
        const agreement = await studentContext.request.patch(`/api/communication/courses/${course.id}/code-of-conduct/agreement`);
        expect(agreement.ok()).toBe(true);
        const parentResponse = await studentContext.request.post(`/api/communication/courses/${course.id}/messages`, {
            data: { content: 'Question in another conversation', conversation: { id: otherChannel.id, type: 'channel' } },
        });
        expect(parentResponse.ok()).toBe(true);
        const parent: { id: number } = await parentResponse.json();
        const currentMessage = await page.request.post(`/api/communication/courses/${course.id}/messages`, {
            data: { content: 'Recent message remains readable', conversation: { id: currentChannel.id, type: 'channel' } },
        });
        expect(currentMessage.ok()).toBe(true);

        const studentPage = await studentContext.newPage();
        const subscriptions = trackWebsocketSubscriptions(studentPage);
        await studentPage.goto(`/courses/${course.id}/communication?conversationId=${currentChannel.id}`);
        await expect(studentPage.getByTestId('conversation-name')).toContainText(currentChannel.name!);
        await subscriptions.waitFor(`/user/topic/notification/${course.id}`);
        // The notifications are addressed to the user: every node has to know the new session before the replies are sent.
        await subscriptions.waitUntilKnownToAllNodes();
        const input = studentPage.getByTestId('formatted-message-input');
        const draft = 'Continue typing while another reply arrives';
        await input.fill(draft);
        await input.press('End');
        const selectionBefore = await input.evaluate(() => window.getSelection()?.anchorOffset);

        const replyResponse = await page.request.post(`/api/communication/courses/${course.id}/answer-messages`, {
            data: { content: 'Reply notification layout regression', post: { id: parent.id } },
        });
        expect(replyResponse.ok()).toBe(true);
        let reply: { id: number } = await replyResponse.json();
        const popup = studentPage.getByTestId('notification-popup-card').filter({ hasText: 'Reply notification layout regression' });
        // Hover waits for the enter animation to finish before measuring the card.
        await popup.hover();
        const popupBox = (await popup.boundingBox())!;
        const send = studentPage.getByRole('button', { name: 'Send', exact: true });
        const sendBox = (await send.boundingBox())!;
        const overlapWidth = Math.max(0, Math.min(popupBox.x + popupBox.width, sendBox.x + sendBox.width) - Math.max(popupBox.x, sendBox.x));
        const overlapHeight = Math.max(0, Math.min(popupBox.y + popupBox.height, sendBox.y + sendBox.height) - Math.max(popupBox.y, sendBox.y));
        expect(overlapWidth * overlapHeight, 'notification must not cover Send').toBe(0);
        await send.click({ trial: true });
        await expect(input).toHaveText(draft);
        await expect(input).toBeFocused();
        expect(await input.evaluate(() => window.getSelection()?.anchorOffset)).toBe(selectionBefore);
        await studentPage.screenshot({ path: testInfo.outputPath('conversation-notification-layout.png') });

        const cards = studentPage.getByTestId('notification-popup-card');
        let latestContent = 'Reply notification layout regression';
        for (let index = 1; index <= 7; index++) {
            latestContent = `Long notification ${index}: ${'Long message for the notification layout. '.repeat(30)}`;
            const response = await page.request.post(`/api/communication/courses/${course.id}/answer-messages`, {
                data: { content: latestContent, post: { id: parent.id } },
            });
            expect(response.ok()).toBe(true);
            reply = await response.json();
            // Notifications are delivered asynchronously. Wait for each popup, so a delayed delivery is not mistaken for a missing one.
            await expect(cards).toHaveCount(index + 1);
        }
        await expect(cards).toHaveCount(8);
        await studentPage.setViewportSize({ width: 390, height: 844 });
        const latestCard = cards.last();
        await latestCard.hover();
        const latestBox = (await latestCard.boundingBox())!;
        const toggle = studentPage.getByTestId('notification-popup-toggle');
        const toggleBox = (await toggle.boundingBox())!;
        expect(toggleBox.y, 'stack controls must stay below long notification cards').toBeGreaterThanOrEqual(latestBox.y + latestBox.height);
        await toggle.click();
        await expect(toggle).toHaveAttribute('aria-expanded', 'true');
        const overlay = studentPage.getByTestId('notification-popup-overlay');
        await expect.poll(() => overlay.evaluate((node) => node.scrollHeight > node.clientHeight)).toBe(true);
        await cards.first().scrollIntoViewIfNeeded();
        const firstBox = (await cards.first().boundingBox())!;
        const overlayBox = (await overlay.boundingBox())!;
        expect(firstBox.y, 'the oldest notification must scroll into view').toBeGreaterThanOrEqual(overlayBox.y - 1);
        const replyLink = latestCard.getByRole('link');
        await expect(replyLink).toHaveAttribute('href', new RegExp(`focusReplyId=${reply.id}(?:&|$)`));
        await replyLink.click();
        // The communication view consumes the highlight parameters after opening the thread.
        await expect(studentPage).toHaveURL(new RegExp(`messageId=${parent.id}(?:&|$)`));
        await expect(studentPage.getByTestId('conversation-thread-card').getByText(latestContent.trim(), { exact: true })).toBeVisible();
    } finally {
        await studentContext.close();
        await courseManagementAPIRequests.deleteCourse(course, admin);
    }
});

test('Long system notifications stay inside the viewport and scroll to their final text', async ({ page }) => {
    const finalText = 'End of maintenance announcement.';
    const text = `${'The platform will be unavailable during planned maintenance. '.repeat(60)}${finalText}`;
    await page.route('**/api/notification/public/system-notifications/active', (route) =>
        route.fulfill({
            json: [
                {
                    id: Date.now(),
                    title: 'Planned maintenance',
                    text,
                    type: 'WARNING',
                    notificationDate: new Date(Date.now() - 60_000).toISOString(),
                    expireDate: new Date(Date.now() + 3_600_000).toISOString(),
                },
            ],
        }),
    );
    await page.goto('/sign-in');
    const trigger = page.getByTestId('system-notification-message');
    const details = page.getByTestId('system-notification-details');

    await forEachViewport(page, async () => {
        await trigger.focus();
        await trigger.press('Enter');
        await expect(details).toContainText(finalText);
        await expectWithinViewport(details, ['top', 'right', 'bottom', 'left'], { name: 'full system notification' });
        await expect.poll(() => details.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true);
        await details.focus();
        await details.press('End');
        await expect.poll(() => details.evaluate((element) => element.scrollHeight - element.clientHeight - element.scrollTop)).toBeLessThanOrEqual(1);
        await expect
            .poll(() =>
                details.evaluate((element, ending) => {
                    const textNode = element.querySelector('p')?.firstChild;
                    if (!textNode) return false;
                    const length = textNode.textContent?.length ?? 0;
                    const range = document.createRange();
                    range.setStart(textNode, Math.max(0, length - ending.length));
                    range.setEnd(textNode, length);
                    const finalLine = range.getBoundingClientRect();
                    const panel = element.getBoundingClientRect();
                    return finalLine.top >= panel.top && finalLine.bottom <= panel.bottom + 1;
                }, finalText),
            )
            .toBe(true);
        await details.press('Escape');
        await expect(details).toBeHidden();
    });
});
