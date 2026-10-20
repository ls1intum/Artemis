import { expect, Page, TestInfo } from '@playwright/test';
import path from 'node:path';
import { Course } from 'app/course/shared/entities/course.model';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { Post } from 'app/communication/shared/entities/post.model';
import { Channel } from 'app/communication/shared/entities/conversation/channel.model';
import { SEED_COURSES, SEED_CHANNELS } from '../../support/seedData';
import { Commands } from '../../support/commands';
import { expectInside } from '../../support/layout';

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
            // The route is captured before the reload, because a reload that drifts to the bare /courses fallback
            // would leave the page somewhere the message cannot be; restoreRouteIfDrifted returns to it.
            // No retry beyond that: the conversation used to fail to re-activate from the conversationId query
            // parameter, since the route emits it while the request that loads the conversations is still in flight
            // and the activation was dropped rather than deferred. CourseConversationsService now remembers it and
            // applies it when the list arrives, so one reload has to be enough.
            const conversationUrl = page.url();
            await Commands.reloadAndRestoreRoute(page, conversationUrl);
            await courseMessages.getSinglePost(message.id!).waitFor({ state: 'visible' });
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
                    await page.getByTestId('posting-context-menu').waitFor({ state: 'visible', timeout: 3000 });
                    break;
                } catch {
                    if (attempt === 2) throw new Error('Context menu did not appear');
                }
            }
            // Verify the pin option is NOT in the dropdown
            await expect(page.getByTestId('posting-menu-pin')).toHaveCount(0);
            // Verify other options ARE present (bookmark, reply, forward)
            await expect(page.getByTestId('posting-menu-bookmark')).toBeVisible();
            await expect(page.getByTestId('posting-menu-reply')).toBeVisible();
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
            const matchedMessage = page.getByText(uniqueSearchText, { exact: true });

            // Server-side message indexing is asynchronous under heavy multi-node load,
            // so a freshly-created message can return no hits for several seconds. Re-issue
            // the search until either the matching post shows up or we run out of attempts.
            const maxAttempts = 5;
            for (let attempt = 0; attempt < maxAttempts; attempt++) {
                await searchInput.fill('');
                await searchInput.fill(uniqueSearchText);
                await searchInput.press('Enter');
                await expect(page.getByRole('heading', { name: /search results/i })).toBeVisible({ timeout: 15_000 });
                try {
                    await expect(matchedMessage).toBeVisible({ timeout: 5_000 });
                    return;
                } catch {
                    if (attempt === maxAttempts - 1) {
                        throw new Error(`Search did not surface message "${uniqueSearchText}" after ${maxAttempts} attempts`);
                    }
                    await page.waitForTimeout(2_000);
                }
            }
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

    test.describe('Emoji shortcode autocomplete', () => {
        test('Student can accept an emoji shortcode suggestion and send the message', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);

            await courseMessages.typeInMessageEditor('Nice :jo');

            // The suggest widget opens after typing ":" plus at least one letter and offers a ":joy:" row.
            // "jo" fuzzy-matches several shortcodes (":joystick:", ":banjo:", ...), so the row is selected
            // explicitly by its shortcode rather than relying on whichever entry Monaco highlights by default.
            // The accessible name of a row is the label plus a kind suffix (e.g. "😂 :joy:, Constant"), so the
            // shortcode is matched as a substring; ":joy:" cannot collide with ":joy_cat:" or ":joystick:".
            const suggestWidget = courseMessages.getSuggestWidget();
            await expect(suggestWidget).toBeVisible({ timeout: 10000 });
            const joySuggestion = suggestWidget.getByRole('option', { name: /:joy:/ });
            await expect(joySuggestion).toBeVisible();

            // Accepting the suggestion (click) replaces the shortcode with the native emoji glyph
            await joySuggestion.click();
            await expect(suggestWidget).toBeHidden();
            const editorText = courseMessages.getMessageEditorText();
            await expect(editorText).toContainText('😂');
            await expect(editorText).not.toContainText(':jo');

            // The editor propagates content to the send form with a 200ms debounce, and the Enter handler
            // silently drops the submit while the form is still empty/invalid. The send button's enabled
            // state mirrors form validity, so wait for it before sending (a real user is slower than 200ms).
            await courseMessages.waitUntilSendEnabled();

            const message = await courseMessages.sendMessageWithEnterKey();
            expect(message.content!).toContain('😂');
            await courseMessages.checkMessage(message.id!, '😂');
        });

        test('Typing a colon-separated number does not open the suggest widget', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${seedChannelId}`);

            await courseMessages.typeInMessageEditor('10:30');

            // The character before the trigger colon is a digit, not whitespace, so the word-boundary
            // check rejects the trigger and the widget must stay closed. The editor text is asserted
            // first: it waits until all keystrokes are rendered, so the hidden check afterwards is
            // meaningful (a broken boundary check would have opened the widget while typing ":3").
            await expect(courseMessages.getMessageEditorText()).toContainText('10:30');
            await expect(courseMessages.getSuggestWidget()).toBeHidden();
        });
    });

    test.describe('Message forwarding', () => {
        // self-contained source + destination channels so membership and names are deterministic
        let sourceChannel: Channel;
        let destinationChannel: Channel;
        let sourcePost: Post;
        let reply: Post;

        test.beforeEach('Create source/destination channels, a message and a reply', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            const uid = generateUUID().slice(0, 8);
            const courseRef = { id: writeCourse.id } as any;
            sourceChannel = await communicationAPIRequests.createCourseMessageChannel(courseRef, `fwd-src-${uid}`, 'Forward source', false, true);
            destinationChannel = await communicationAPIRequests.createCourseMessageChannel(courseRef, `fwd-dst-${uid}`, 'Forward destination', false, true);
            // the forwarder (instructor) must participate in both: in the source to forward from it, in the destination to target and view it
            await communicationAPIRequests.joinUserIntoChannel(courseRef, sourceChannel.id!, instructor);
            await communicationAPIRequests.joinUserIntoChannel(courseRef, destinationChannel.id!, instructor);
            sourcePost = await communicationAPIRequests.createCourseMessage(courseRef, sourceChannel.id!, 'channel', `Forward source message ${uid}`);
            reply = await communicationAPIRequests.createCourseMessageReply(courseRef, sourcePost, `Forward reply message ${uid}`);
        });

        test('Forwarded post renders its preview in the destination conversation', async ({ login, courseMessages }) => {
            await login(instructor);
            await courseMessages.openConversationAndWaitForPost(writeCourse.id, sourceChannel.id!, sourcePost.id!);
            await courseMessages.checkMessage(sourcePost.id!, sourcePost.content!);

            await courseMessages.forwardMessageToChannel(sourcePost.id!, destinationChannel.name!);

            // Opening the destination triggers the access-checked source-post fetch; a visible preview proves it succeeded for an
            // accessible source. Reload-retry to absorb the multi-node lag before the just-created forward is visible to the serving node.
            await courseMessages.openConversationAndWaitForForwardedPreview(writeCourse.id, destinationChannel.id!, sourcePost.content!);
        });

        test('Forwarded reply renders its preview in the destination conversation', async ({ login, courseMessages }) => {
            await login(instructor);
            await courseMessages.openConversationAndWaitForPost(writeCourse.id, sourceChannel.id!, sourcePost.id!);
            await courseMessages.checkMessage(sourcePost.id!, sourcePost.content!);

            await courseMessages.forwardReplyToChannel(sourcePost.id!, reply.id!, destinationChannel.name!);

            // Same as the post case, but the access check guards the source-answer fetch; a visible preview proves it returned the reply.
            await courseMessages.openConversationAndWaitForForwardedPreview(writeCourse.id, destinationChannel.id!, reply.content!);
        });
    });
    test.describe('Forwarded preview layout and navigation', () => {
        let course: Course | undefined;
        let source: Channel;
        let destination: Channel;
        let original: Post;
        const longContent = [
            '**Design by contract: preparing for the tutorial**',
            'A method specification states the precondition expected from callers and the postcondition guaranteed by the implementation.',
            'For binary search, explain why the input must be sorted and how the loop invariant narrows the search interval.',
            'Distinguish a representation invariant from an abstraction function when implementing an immutable set.',
            'Give an example in which defensive copying prevents representation exposure through a mutable collection.',
            'Explain why a passing test suite does not establish correctness for every permitted input.',
            'Compare checked and unchecked exceptions when documenting a failure condition in a public API.',
            'For the tutorial discussion, prepare one specification that is too weak and one that is stronger than its implementation.',
        ].join('\n\n');

        async function screenshot(page: Page, info: TestInfo, name: string) {
            const directory = process.env.ARTEMIS_PR_SCREENSHOTS;
            if (directory && info.project.name === 'chromium') {
                await page.screenshot({ path: path.join(directory, name + '.png'), fullPage: true });
            }
        }

        test.beforeEach(async ({ login, courseManagementAPIRequests, communicationAPIRequests }) => {
            await login(admin);
            course = await courseManagementAPIRequests.createCourse({ courseName: 'Software Engineering' });
            await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            source = await communicationAPIRequests.createCourseMessageChannel(course, 'tutorial-questions', 'Questions on specifications and software design', false, true);
            destination = await communicationAPIRequests.createCourseMessageChannel(course, 'exam-preparation', 'Discussion of lecture concepts', false, true);
            for (const channel of [source, destination]) {
                await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, instructor);
                await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, studentOne);
            }
            await login(instructor);
            original = await communicationAPIRequests.createCourseMessage(course, source.id!, 'channel', longContent);
        });

        test.afterEach(async ({ courseManagementAPIRequests }) => {
            await courseManagementAPIRequests.deleteCourse(course, admin);
            course = undefined;
        });

        test('keeps long forwarded posts expandable in light and dark themes and the thread internally scrollable', async ({
            page,
            courseMessages,
            communicationAPIRequests,
        }, info) => {
            await courseMessages.openConversationAndWaitForPost(course!.id!, source.id!, original.id!);
            const forwardedResponse = page.waitForResponse(
                (response) => response.url().endsWith('/messages') && response.request().method() === 'POST' && response.status() === 201,
            );
            await courseMessages.forwardMessageToChannel(original.id!, destination.name!, 'Please prepare these questions for the next tutorial.');
            const forwarded: Post = await (await forwardedResponse).json();
            await courseMessages.openConversationAndWaitForForwardedPreview(course!.id!, destination.id!, 'Design by contract');
            const mainPost = courseMessages.getSinglePost(forwarded.id!);
            const preview = mainPost.getByTestId('forwarded-message-body');
            const expand = mainPost.getByTestId('forwarded-message-expand');
            await expect(preview).toContainText('Design by contract');
            await expect(expand).toHaveAttribute('aria-expanded', 'false');
            const collapsedHeight = await preview.evaluate((element) => element.clientHeight);
            expect(await preview.evaluate((element) => element.scrollHeight)).toBeGreaterThan(collapsedHeight);
            await screenshot(page, info, '01-forwarded-post-light');
            await expand.click();
            await expect(expand).toHaveAttribute('aria-expanded', 'true');
            await expect.poll(() => preview.evaluate((element) => element.clientHeight)).toBeGreaterThan(collapsedHeight);
            await expect(expand).toHaveText('Show less');
            await expand.click();
            await expect(expand).toHaveAttribute('aria-expanded', 'false');
            await expect.poll(() => preview.evaluate((element) => element.clientHeight)).toBe(collapsedHeight);
            if ((await page.locator('html').getAttribute('data-theme')) !== 'dark') {
                await page.getByRole('button', { name: 'Dark Mode', exact: true }).click();
            }
            await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
            await expect(preview).toBeVisible();
            await screenshot(page, info, '02-forwarded-post-dark');

            for (let index = 0; index < 14; index++) {
                await communicationAPIRequests.createCourseMessageReply(
                    course!,
                    forwarded,
                    `Tutorial follow-up ${index + 1}: how does the invariant constrain the implementation?\n\nExplain which preconditions the caller must establish and which postconditions the method guarantees.`,
                );
            }
            await courseMessages.openThreadForMessage(forwarded.id!);
            const card = page.getByTestId('conversation-thread-card');
            const scrollBody = page.getByTestId('conversation-thread-scroll-body');
            await expect(card.getByTestId('forwarded-message-body')).toContainText('Design by contract');
            await expectInside(mainPost.getByRole('button', { name: 'View conversation', exact: true }), mainPost, ['left', 'right'], {
                name: 'Forwarded conversation link in the narrow message column',
            });
            await expect.poll(() => scrollBody.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true);
            const cardBounds = await card.boundingBox();
            expect(cardBounds).not.toBeNull();
            expect(cardBounds!.y + cardBounds!.height).toBeLessThanOrEqual(page.viewportSize()!.height);
            const documentTop = await page.evaluate(() => window.scrollY);
            await scrollBody.evaluate((element) => (element.scrollTop = element.scrollHeight));
            await expect.poll(() => scrollBody.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
            await expect(card.getByText('Tutorial follow-up 14:', { exact: false })).toBeVisible();
            expect(await page.evaluate(() => window.scrollY)).toBe(documentTop);
            await screenshot(page, info, '03-thread-internal-scrolling');
        });

        test('renders a forwarded reply in its thread and navigates to the original conversation', async ({ page, courseMessages, communicationAPIRequests }, info) => {
            const reply = await communicationAPIRequests.createCourseMessageReply(
                course!,
                original,
                'The abstraction function maps the concrete representation to the abstract value.',
            );
            await courseMessages.openConversationAndWaitForPost(course!.id!, source.id!, original.id!);
            const forwardedResponse = page.waitForResponse(
                (response) => response.url().endsWith('/messages') && response.request().method() === 'POST' && response.status() === 201,
            );
            await courseMessages.forwardReplyToChannel(original.id!, reply.id!, destination.name!);
            const forwarded: Post = await (await forwardedResponse).json();
            await courseMessages.openConversationAndWaitForForwardedPreview(course!.id!, destination.id!, 'The abstraction function');
            await courseMessages.openThreadForMessage(forwarded.id!);
            const card = page.getByTestId('conversation-thread-card');
            await expect(card.getByTestId('forwarded-message-body')).toContainText('The abstraction function maps the concrete representation');
            await screenshot(page, info, '04-forwarded-reply-in-thread');
            await card.getByRole('button', { name: 'View conversation', exact: true }).click();
            await expect(page).toHaveURL(new RegExp(`conversationId=${source.id}(?:&|$)`));
            await expect(page.getByTestId('conversation-name')).toHaveText(source.name!);
            await expect(page.getByTestId('conversation-thread-card')).toContainText(reply.content!);
        });

        test('shows a deleted-source placeholder after reloading a forwarded post', async ({ page, courseMessages }, info) => {
            await courseMessages.openConversationAndWaitForPost(course!.id!, source.id!, original.id!);
            await courseMessages.forwardMessageToChannel(original.id!, destination.name!);
            await courseMessages.openConversationAndWaitForForwardedPreview(course!.id!, destination.id!, 'Design by contract');
            const deleted = await page.request.delete(`api/communication/courses/${course!.id}/messages/${original.id}`);
            expect(deleted.ok()).toBe(true);
            await page.reload();
            await expect(page.getByText('This message has been deleted', { exact: false })).toBeVisible();
            await expect(page.getByTestId('forwarded-message-body')).toHaveCount(0);
            await expect(page.getByTestId('forwarded-message-expand')).toHaveCount(0);
            await screenshot(page, info, '05-deleted-source-placeholder');
        });
    });
});
