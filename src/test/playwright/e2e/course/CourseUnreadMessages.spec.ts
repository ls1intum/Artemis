import { BrowserContext, Page, TestInfo, expect } from '@playwright/test';
import path from 'node:path';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentTwo, studentThree, UserCredentials } from '../../support/users';
import { Commands } from '../../support/commands';
import { CommunicationAPIRequests } from '../../support/requests/CommunicationAPIRequests';
import { Course } from 'app/course/shared/entities/course.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Channel } from 'app/communication/shared/entities/conversation/channel.model';

/** Real REST persistence, websocket delivery and the rendered sidebar must agree. */
test.describe('Unread messages across conversation types', { tag: '@fast' }, () => {
    let course: Course | undefined;
    let channel: Channel;
    let other: Channel;
    let staff: Page;
    let previousReplier: Page;
    let bystander: Page;
    const contexts: BrowserContext[] = [];

    function api(page: Page) {
        return new CommunicationAPIRequests(page);
    }

    async function conversation(page: Page, id: number): Promise<ConversationDTO> {
        const response = await page.request.get(`api/communication/courses/${course!.id}/conversations`);
        expect(response.ok()).toBe(true);
        const conversations: ConversationDTO[] = await response.json();
        const match = conversations.find((item) => item.id === id);
        expect(match).toBeDefined();
        return match!;
    }

    async function unread(page: Page, id: number) {
        return (await conversation(page, id)).unreadMessagesCount ?? 0;
    }

    async function markRead(page: Page, id: number) {
        const response = await page.request.patch(`api/communication/courses/${course!.id}/conversations/${id}/mark-as-read`);
        expect(response.ok()).toBe(true);
        expect(await unread(page, id)).toBe(0);
    }

    async function showGroups(page: Page) {
        for (const group of ['General', 'Group Chats', 'Direct Messages']) {
            const button = page.getByRole('button', { name: new RegExp(`^${group} \\(`) });
            if ((await button.count()) && (await button.getAttribute('aria-expanded')) !== 'true') {
                await button.click();
            }
        }
    }

    function conversationLink(page: Page, id: number) {
        return page.getByTestId(`conversation-link-${id}`).first();
    }

    async function screenshot(page: Page, info: TestInfo, name: string) {
        const directory = process.env.ARTEMIS_PR_SCREENSHOTS;
        if (directory && info.project.name === 'chromium') {
            await page.screenshot({ path: path.join(directory, `${name}.png`), fullPage: true });
        }
    }

    test.beforeEach(async ({ browser, page, login, courseManagementAPIRequests, communicationAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ courseName: 'Software Construction' });
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        for (const user of [studentOne, studentTwo, studentThree]) {
            await courseManagementAPIRequests.addStudentToCourse(course, user);
        }
        channel = await communicationAPIRequests.createCourseMessageChannel(course, 'concurrency', 'Lecture questions about concurrency and thread safety', false, true);
        other = await communicationAPIRequests.createCourseMessageChannel(course, 'tutorial-planning', 'Preparation for the next tutorial', false, true);
        for (const target of [channel, other]) {
            for (const user of [studentOne, studentTwo, studentThree, instructor]) {
                await communicationAPIRequests.joinUserIntoChannel(course, target.id!, user);
            }
        }
        async function session(user: UserCredentials) {
            const context = await browser.newContext({ baseURL: test.info().project.use.baseURL, serviceWorkers: 'block' });
            contexts.push(context);
            const userPage = await context.newPage();
            await Commands.login(userPage, user);
            const agreement = await userPage.request.patch(`api/communication/courses/${course!.id}/code-of-conduct/agreement`);
            expect(agreement.ok()).toBe(true);
            return userPage;
        }
        staff = await session(instructor);
        previousReplier = await session(studentTwo);
        bystander = await session(studentThree);
        await login(studentOne);
        await api(page).acceptCodeOfConduct(course);
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await Promise.all(contexts.splice(0).map((context) => context.close()));
        await courseManagementAPIRequests.deleteCourse(course, admin);
        course = undefined;
    });

    for (const kind of ['channel', 'groupChat', 'oneToOneChat'] as const) {
        test(`delivers a ${kind} reply badge live and clears it only when the recipient opens that conversation`, async ({ page, courseMessages }, info) => {
            let id = channel.id!;
            if (kind === 'groupChat') {
                const group = await api(page).createCourseMessageGroupChat(course!, [instructor.username, studentTwo.username, studentThree.username]);
                await api(page).updateCourseMessageGroupChatName(course!, group, 'concurrency-study');
                id = group.id!;
            } else if (kind === 'oneToOneChat') {
                id = (await api(page).createOneToOneChat(course!, instructor.username)).id;
            }
            const root = await api(page).createCourseMessage(course!, id, kind, 'Why is a shared HashMap unsafe when two threads update it concurrently?');
            if (kind !== 'oneToOneChat') {
                await api(previousReplier).createCourseMessageReply(course!, root, 'Does the update consist of several operations that can interleave?');
                await markRead(previousReplier, id);
                await markRead(bystander, id);
            }
            await markRead(page, id);
            await page.goto(`/courses/${course!.id}/communication?conversationId=${other.id}`);
            await expect(page.getByTestId('conversation-name')).toHaveText(other.name!);
            await showGroups(page);
            const link = conversationLink(page, id);
            await expect(link).toBeVisible();
            await expect(link.getByTestId('conversation-unread-count')).toHaveCount(0);

            const reply = await api(staff).createCourseMessageReply(course!, root, 'Correct: identify the critical section and protect the whole invariant with the same lock.');

            await expect(link.getByTestId('conversation-unread-count')).toHaveText('1');
            expect(await unread(page, id)).toBe(1);
            if (kind !== 'oneToOneChat') {
                expect(await unread(previousReplier, id)).toBe(1);
                expect(await unread(bystander, id)).toBe(0);
            }
            const courseUnread = await page.request.get(`api/communication/courses/${course!.id}/unread-messages`);
            expect(courseUnread.ok()).toBe(true);
            expect(await courseUnread.json()).toBe(true);
            await screenshot(page, info, `01-${kind}-live-reply-badge`);
            await link.click();
            await expect(page).toHaveURL(new RegExp(`conversationId=${id}$`));
            await expect(courseMessages.getSinglePost(root.id!)).toContainText(root.content!);
            await expect.poll(() => unread(page, id)).toBe(0);
            await expect(link.getByTestId('conversation-unread-count')).toHaveCount(0);
            await courseMessages.openThreadForMessage(root.id!);
            await expect(page.locator('jhi-conversation-thread-sidebar')).toContainText(reply.content!);
        });
    }

    test('preserves muted and manually unread conversations while replies arrive', async ({ page, courseMessages }, info) => {
        const root = await api(staff).createCourseMessage(course!, channel.id!, 'channel', 'Which invariants must remain true while another thread is executing?');
        await api(page).createCourseMessageReply(course!, root, 'The object should preserve its representation invariant at the synchronization boundary.');
        await markRead(page, channel.id!);
        const muted = await page.request.post(`api/communication/courses/${course!.id}/conversations/${channel.id}/muted`, { params: { isMuted: true } });
        expect(muted.ok()).toBe(true);
        await page.goto(`/courses/${course!.id}/communication?conversationId=${other.id}`);
        await expect(page.getByTestId('conversation-name')).toHaveText(other.name!);
        await showGroups(page);
        const link = conversationLink(page, channel.id!);
        await api(staff).createCourseMessageReply(course!, root, 'Exactly. A lock is useful only if every access follows the locking policy.');
        expect(await unread(page, channel.id!)).toBe(0);
        await expect(link.getByTestId('conversation-unread-count')).toHaveCount(0);
        const unmuted = await page.request.post(`api/communication/courses/${course!.id}/conversations/${channel.id}/muted`, { params: { isMuted: false } });
        expect(unmuted.ok()).toBe(true);
        await page.reload();
        await expect(page.getByTestId('conversation-name')).toHaveText(other.name!);
        await showGroups(page);
        await link.click();
        await expect(courseMessages.getSinglePost(root.id!)).toContainText(root.content!);
        await expect.poll(() => unread(page, channel.id!)).toBe(0);
        await courseMessages.getSinglePost(root.id!).getByText(root.content!, { exact: true }).click({ button: 'right' });
        await page.getByTestId('posting-menu-mark-unread').click();
        await expect.poll(() => unread(page, channel.id!)).toBe(1);
        await api(staff).createCourseMessageReply(course!, root, 'Try expressing the locking policy in the class documentation.');
        await expect(link.getByTestId('conversation-unread-count')).toHaveText('2');
        expect(await unread(page, channel.id!)).toBe(2);
        await screenshot(page, info, '02-manually-unread-open-conversation');
    });

    test('deleting a read post preserves unread replies and moderator deletion excludes the actual author', async ({ page }, info) => {
        const root = await api(staff).createCourseMessage(course!, channel.id!, 'channel', 'Can a representation invariant span several fields?');
        await api(page).createCourseMessageReply(course!, root, 'Yes, for example the collection size and a cached count.');
        await api(previousReplier).createCourseMessageReply(course!, root, 'Both fields must be updated atomically with respect to readers.');
        const readPost = await api(staff).createCourseMessage(course!, channel.id!, 'channel', 'The lecture notes on thread safety are available.');
        await markRead(page, channel.id!);
        await markRead(previousReplier, channel.id!);
        await page.goto(`/courses/${course!.id}/communication?conversationId=${other.id}`);
        await expect(page.getByTestId('conversation-name')).toHaveText(other.name!);
        await showGroups(page);
        const badge = conversationLink(page, channel.id!).getByTestId('conversation-unread-count');
        const newPost = await api(previousReplier).createCourseMessage(course!, channel.id!, 'channel', 'Should all reads use the same lock as updates?');
        expect(await unread(page, channel.id!)).toBe(1);
        await expect(badge).toHaveText('1');
        // Sending a post marks its author's conversation read asynchronously. Complete that before the new reply,
        // so the author's remaining unread count really belongs to the reply when a moderator deletes the post.
        await expect
            .poll(async () => new Date((await conversation(previousReplier, channel.id!)).lastReadDate!.toString()).getTime())
            .toBeGreaterThanOrEqual(new Date(newPost.creationDate!.toString()).getTime());
        expect(await unread(previousReplier, channel.id!)).toBe(0);
        await api(staff).createCourseMessageReply(course!, root, 'Use one synchronization protocol for all fields involved in the invariant.');
        expect(await unread(page, channel.id!)).toBe(2);
        await expect(badge).toHaveText('2');
        expect(await unread(previousReplier, channel.id!)).toBe(1);
        const deleteRead = await staff.request.delete(`api/communication/courses/${course!.id}/messages/${readPost.id}`);
        expect(deleteRead.ok()).toBe(true);
        expect(await unread(page, channel.id!)).toBe(2);
        expect(await unread(previousReplier, channel.id!)).toBe(1);
        await expect(badge).toHaveText('2');
        const deleteUnread = await staff.request.delete(`api/communication/courses/${course!.id}/messages/${newPost.id}`);
        expect(deleteUnread.ok()).toBe(true);
        await expect.poll(() => unread(page, channel.id!)).toBe(1);
        expect(await unread(previousReplier, channel.id!)).toBe(1);
        await expect(badge).toHaveText('1');
        await screenshot(page, info, '03-unread-reply-preserved-after-deletion');
    });

    test('All Messages initially shows the newest page rather than the oldest course posts', async ({ page, courseMessages }, info) => {
        const oldest = await api(staff).createCourseMessage(course!, channel.id!, 'channel', 'First lecture: specifications describe what a program must do.');
        for (let start = 0; start < 60; start += 10) {
            await Promise.all(
                Array.from({ length: 10 }, (_, index) =>
                    api(staff).createCourseMessage(
                        course!,
                        channel.id!,
                        'channel',
                        `Tutorial discussion ${start + index + 1}: explain how specifications, invariants and tests complement each other.`,
                    ),
                ),
            );
        }
        const newest = await api(staff).createCourseMessage(course!, channel.id!, 'channel', 'Next tutorial: prepare your questions about thread safety and synchronization.');
        await page.goto(`/courses/${course!.id}/communication?conversationId=${other.id}`);
        await expect(page.getByTestId('conversation-name')).toHaveText(other.name!);
        await courseMessages.searchCourseWide('');
        await expect(courseMessages.getSinglePost(newest.id!)).toBeVisible();
        await expect(courseMessages.getSinglePost(newest.id!)).toContainText(newest.content!);
        await expect(courseMessages.getSinglePost(oldest.id!)).toHaveCount(0);
        await screenshot(page, info, '04-all-messages-newest-page');
    });
});
