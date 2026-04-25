import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { Post } from 'app/communication/shared/entities/post.model';
import { SEED_COURSES, SEED_CHANNELS } from '../../support/seedData';

const writeCourse = { id: SEED_COURSES.channel2.id };
// Use the pre-seeded "random" channel — students are already joined
const seedChannelId = SEED_CHANNELS.channel2.random;

test.describe('Message interactions', { tag: '@fast' }, () => {
    test.describe('Bookmark/save toggle', () => {
        let message: Post;

        test.beforeEach('Create message in seed channel', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            message = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, seedChannelId, 'channel', 'Bookmark test ' + generateUUID().slice(0, 8));
        });

        test('Student should be able to bookmark a message', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            // Verify not bookmarked initially
            await courseMessages.checkMessageBookmarked(message.id!, false);
            // Bookmark the message
            await courseMessages.bookmarkMessage(message.id!);
            // Verify the bookmark indicator appears
            await courseMessages.checkMessageBookmarked(message.id!, true);
        });

        test('Student should be able to remove bookmark from a message', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            // Bookmark first
            await courseMessages.bookmarkMessage(message.id!);
            await courseMessages.checkMessageBookmarked(message.id!, true);
            // Remove bookmark
            await courseMessages.bookmarkMessage(message.id!);
            // Verify the bookmark indicator is gone
            await courseMessages.checkMessageBookmarked(message.id!, false);
        });

        test('Bookmarked message should persist after page reload', async ({ login, courseMessages, page }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            await courseMessages.bookmarkMessage(message.id!);
            await courseMessages.checkMessageBookmarked(message.id!, true);
            // Reload page completely
            await page.reload();
            // Verify the message is still visible and still bookmarked
            await courseMessages.checkMessage(message.id!, message.content!);
            await courseMessages.checkMessageBookmarked(message.id!, true);
        });
    });

    test.describe('Emoji reactions', () => {
        let message: Post;

        test.beforeEach('Create message in seed channel', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            message = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, seedChannelId, 'channel', 'React test ' + generateUUID().slice(0, 8));
        });

        test('Student should be able to add an emoji reaction to a message', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            // Add a thumbsup reaction
            await courseMessages.addReactionToMessage(message.id!, 'thumbsup');
            // Verify the reaction emoji container appears with count 1
            await courseMessages.checkReactionOnMessage(message.id!);
            const postLocator = courseMessages.getSinglePost(message.id!);
            await expect(postLocator.locator('.emoji-container .emoji-count').first()).toContainText('1');
        });

        test('Student should be able to remove their own reaction', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            // Add reaction
            await courseMessages.addReactionToMessage(message.id!, 'thumbsup');
            await courseMessages.checkReactionOnMessage(message.id!);
            // Click the reaction button to toggle it off
            const postLocator = courseMessages.getSinglePost(message.id!);
            const reactionButton = postLocator.locator('.emoji-container').first().locator('..');
            const responsePromise = courseMessages['page'].waitForResponse((resp: any) => resp.url().includes('/postings/reactions') && resp.request().method() === 'DELETE');
            await reactionButton.click();
            await responsePromise;
            // Verify the reaction emoji container disappears
            await expect(postLocator.locator('.emoji-container')).toHaveCount(0, { timeout: 10000 });
        });

        test('Multiple users can react to the same message', async ({ login, courseMessages, communicationAPIRequests }) => {
            // Add reactions from two users via API for reliable setup
            await login(instructor);
            await communicationAPIRequests.addReactionToPost(writeCourse.id, message.id!, '+1');
            await login(studentOne);
            await communicationAPIRequests.addReactionToPost(writeCourse.id, message.id!, '+1');
            // Navigate and verify count shows 2
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            const postLocator = courseMessages.getSinglePost(message.id!);
            await expect(postLocator.locator('.emoji-container .emoji-count').first()).toContainText('2', { timeout: 10000 });
            // Verify the reaction button shows the student's own reaction as "reacted"
            await expect(postLocator.locator('.reaction-button--reacted').first()).toBeVisible();
        });
    });

    test.describe('Reply/thread creation', () => {
        let message: Post;

        test.beforeEach('Create message in seed channel', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            message = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, seedChannelId, 'channel', 'Thread test ' + generateUUID().slice(0, 8));
        });

        test('Student should be able to open thread sidebar and reply', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            // Open the thread sidebar
            await courseMessages.openThreadForMessage(message.id!);
            // Verify the original message is visible in the thread sidebar
            const threadSidebar = courseMessages['page'].locator('.expanded-thread');
            await expect(threadSidebar).toBeVisible();
            await expect(threadSidebar.locator('.markdown-preview', { hasText: message.content! })).toBeVisible();
            // Write and submit a reply
            const replyContent = 'Thread reply ' + generateUUID().slice(0, 8);
            const reply = await courseMessages.replyInThread(replyContent);
            // Verify the reply appears in the thread with correct content
            await courseMessages.checkThreadReply(reply.id!, replyContent);
        });

        test('Thread sidebar should show existing replies', async ({ login, courseMessages, communicationAPIRequests }) => {
            // Create two replies via API
            const reply1Content = 'First reply ' + generateUUID().slice(0, 8);
            const reply2Content = 'Second reply ' + generateUUID().slice(0, 8);
            const reply1 = await communicationAPIRequests.createCourseMessageReply({ id: writeCourse.id } as any, message, reply1Content);
            const reply2 = await communicationAPIRequests.createCourseMessageReply({ id: writeCourse.id } as any, message, reply2Content);
            // Navigate and open thread
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            await courseMessages.openThreadForMessage(message.id!);
            // Verify both replies are visible with correct content
            await courseMessages.checkThreadReply(reply1.id!, reply1Content);
            await courseMessages.checkThreadReply(reply2.id!, reply2Content);
        });

        test('Reply should show answer count on the original message', async ({ login, courseMessages, communicationAPIRequests }) => {
            // Create two replies via API
            await communicationAPIRequests.createCourseMessageReply({ id: writeCourse.id } as any, message, 'Reply 1');
            await communicationAPIRequests.createCourseMessageReply({ id: writeCourse.id } as any, message, 'Reply 2');
            // Navigate and verify the answer count shows on the original post
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            const postLocator = courseMessages.getSinglePost(message.id!);
            const expandBtn = postLocator.locator('.post-reactions-bar .expand-answers-btn');
            await expect(expandBtn).toBeVisible({ timeout: 10000 });
            // Verify the count text shows "2"
            await expect(expandBtn).toContainText('2');
        });
    });

    test.describe('Message pinning', () => {
        let message: Post;

        test.beforeEach('Create message in seed channel', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            message = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, seedChannelId, 'channel', 'Pin test ' + generateUUID().slice(0, 8));
        });

        test('Instructor should be able to pin and unpin a message', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            // Pin the message
            await courseMessages.pinMessage(message.id!);
            // Verify the pinned visual indicator appears on the inner post element
            const postLocator = courseMessages.getSinglePost(message.id!);
            await expect(postLocator.locator('.pinned-message')).toBeVisible({ timeout: 10000 });
            // Unpin the message
            await courseMessages.pinMessage(message.id!);
            // Verify the pinned indicator is removed
            await expect(postLocator.locator('.pinned-message')).toHaveCount(0, { timeout: 10000 });
        });
    });

    test.describe('Mark answer as resolving', () => {
        let message: Post;
        let reply: Post;

        test.beforeEach('Create message and reply in seed channel', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            message = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, seedChannelId, 'channel', 'Resolve test ' + generateUUID().slice(0, 8));
            reply = await communicationAPIRequests.createCourseMessageReply({ id: writeCourse.id } as any, message, 'Answer to resolve ' + generateUUID().slice(0, 8));
        });

        test('Instructor should be able to mark and unmark an answer as resolving', async ({ login, courseMessages, page }) => {
            await login(instructor, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            // Open the thread to see the reply
            await courseMessages.openThreadForMessage(message.id!);
            await courseMessages.checkThreadReply(reply.id!, reply.content!);
            // Hover over the reply to reveal the reaction bar with the resolve button
            const threadSidebar = page.locator('.expanded-thread');
            const replyLocator = threadSidebar.locator(`#item-${reply.id!}`);
            await replyLocator.locator('.message-container').hover();
            const resolveButton = replyLocator.locator('#toggleElement');
            await resolveButton.waitFor({ state: 'visible', timeout: 5000 });
            // Mark as resolving
            const responsePromise = page.waitForResponse((resp) => resp.url().includes('/answer-messages/') && resp.request().method() === 'PUT');
            await resolveButton.click();
            await responsePromise;
            // Verify the resolved icon appears in the always-visible emoji-count bar
            await expect(replyLocator.locator('.resolved')).toBeVisible({ timeout: 10000 });
            // Hover again and unmark
            await replyLocator.locator('.message-container').hover();
            await resolveButton.waitFor({ state: 'visible', timeout: 5000 });
            const responsePromise2 = page.waitForResponse((resp) => resp.url().includes('/answer-messages/') && resp.request().method() === 'PUT');
            await resolveButton.click();
            await responsePromise2;
            // Verify the resolved icon is gone and the notResolved icon shows
            await expect(replyLocator.locator('.resolved')).toHaveCount(0, { timeout: 10000 });
        });

        test('Student who is not the author cannot resolve an answer', async ({ login, courseMessages, page }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            // Open the thread
            await courseMessages.openThreadForMessage(message.id!);
            await courseMessages.checkThreadReply(reply.id!, reply.content!);
            // The resolve button should not be clickable for a non-author student
            const threadSidebar = page.locator('.expanded-thread');
            const replyLocator = threadSidebar.locator(`#item-${reply.id!}`);
            // The button exists but should not have clickable behavior (no .clickable class or icon hidden)
            // For non-author students, the resolve icon should not be visible at all
            await expect(replyLocator.locator('.notResolved')).toHaveCount(0, { timeout: 5000 });
        });
    });

    test.describe('Saved posts view', () => {
        let message: Post;

        test.beforeEach('Create and bookmark a message', async ({ login, communicationAPIRequests, courseMessages }) => {
            await login(admin);
            message = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, seedChannelId, 'channel', 'Saved post test ' + generateUUID().slice(0, 8));
        });

        test('Student should be able to view bookmarked messages in saved posts', async ({ login, courseMessages, page }) => {
            // First bookmark a message
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            await courseMessages.bookmarkMessage(message.id!);
            await courseMessages.checkMessageBookmarked(message.id!, true);
            // Navigate to the saved posts view via URL
            await page.goto(`/courses/${writeCourse.id}/communication?conversationId=in_progress`);
            // Verify the saved posts container is visible
            await expect(page.locator('.saved-posts')).toBeVisible({ timeout: 15000 });
            // Verify the saved posts heading shows
            await expect(page.locator('.saved-posts h4')).toBeVisible();
            // Verify the bookmarked message appears in the saved posts list
            await expect(page.locator('.saved-posts-post-container')).toBeVisible();
            await expect(page.getByText(message.content!)).toBeVisible({ timeout: 10000 });
        });

        test('Empty saved posts view should show empty state', async ({ login, page }) => {
            // Navigate directly to archived saved posts (unlikely to have any)
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=archived`);
            // Verify the saved posts container is visible
            await expect(page.locator('.saved-posts')).toBeVisible({ timeout: 15000 });
            // Verify the empty notice appears
            await expect(page.locator('.saved-posts-empty-notice')).toBeVisible({ timeout: 10000 });
        });
    });

    test.describe('Student cannot pin messages', () => {
        let message: Post;

        test.beforeEach('Create message in seed channel', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            message = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, seedChannelId, 'channel', 'Student pin test ' + generateUUID().slice(0, 8));
        });

        test('Student should not see the pin option in the context menu', async ({ login, courseMessages, page }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);
            await courseMessages.checkMessage(message.id!, message.content!);
            const postLocator = courseMessages.getSinglePost(message.id!);
            // Right-click to open context menu
            for (let attempt = 0; attempt < 3; attempt++) {
                await postLocator.locator('.message-container').click({ button: 'right' });
                try {
                    await page.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 3000 });
                    break;
                } catch {
                    if (attempt === 2) throw new Error('Context menu did not appear');
                }
            }
            // Verify the pin option is NOT in the dropdown
            await expect(page.locator('.dropdown-menu.show .dropdown-item', { hasText: /pin/i })).toHaveCount(0);
            // Verify other options ARE present (bookmark, reply, forward)
            await expect(page.locator('.dropdown-menu.show .dropdown-item', { hasText: /save|bookmark/i })).toBeVisible();
            await expect(page.locator('.dropdown-menu.show .dropdown-item', { hasText: /reply/i })).toBeVisible();
        });
    });

    test.describe('Message search', () => {
        const uniqueSearchText = 'SearchE2E_' + generateUUID().slice(0, 8);

        test.beforeEach('Create message with unique text in seed channel', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, seedChannelId, 'channel', uniqueSearchText);
        });

        test('Student should be able to search for a message', async ({ login, page }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication`);
            const searchInput = page.getByPlaceholder(/search for a message/i);
            await searchInput.fill(uniqueSearchText);
            await searchInput.press('Enter');
            // Verify search results heading appears
            await expect(page.getByRole('heading', { name: /search results/i })).toBeVisible({ timeout: 15000 });
            // Verify the matching message text is displayed in results (scope to posting thread to avoid matching the heading)
            await expect(page.locator('jhi-posting-thread').getByText(uniqueSearchText)).toBeVisible({ timeout: 10000 });
        });

        test('Search should return no results for non-existent text', async ({ login, page }) => {
            const noMatchText = 'ZZZNoMatch_' + generateUUID();
            await login(studentOne, `/courses/${writeCourse.id}/communication`);
            const searchInput = page.getByPlaceholder(/search for a message/i);
            await searchInput.fill(noMatchText);
            await searchInput.press('Enter');
            // Verify search results heading appears
            await expect(page.getByRole('heading', { name: /search results/i })).toBeVisible({ timeout: 15000 });
            // Verify no message posts are shown in the results area (markdown-preview contains message content)
            await expect(page.locator('.markdown-preview', { hasText: noMatchText })).toHaveCount(0, { timeout: 5000 });
        });
    });
});
