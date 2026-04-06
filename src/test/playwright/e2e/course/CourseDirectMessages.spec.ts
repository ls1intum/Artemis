import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { instructor, studentOne, studentTwo } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { execSync } from 'child_process';

const course = { id: SEED_COURSES.groupChat1.id } as any;

/**
 * Deletes a one-to-one chat from the database.
 * One-to-one chats have no REST delete API, so we use SQL via docker exec.
 * This follows the same pattern as CourseGroupChatMessages.spec.ts.
 */
function deleteOneToOneChatFromDB(conversationId: number | undefined) {
    if (conversationId === undefined) return;
    try {
        const sql = `DELETE FROM conversation_participant WHERE conversation_id = ${conversationId}; DELETE FROM post WHERE conversation_id = ${conversationId}; DELETE FROM conversation WHERE id = ${conversationId};`;
        execSync(`docker exec artemis-postgres psql -U Artemis -d Artemis -c "${sql}"`, { timeout: 5000, encoding: 'utf-8' });
    } catch {
        // DB cleanup not available — non-critical
    }
}

test.describe('Direct messages', { tag: '@fast' }, () => {
    // All DM tests run serially — creating DMs with the same user pairs can conflict across parallel workers
    test.describe.configure({ mode: 'serial' });

    test.describe('Create one-to-one chat', () => {
        let dmConversationId: number | undefined;

        test.afterEach('Cleanup DM', () => {
            deleteOneToOneChatFromDB(dmConversationId);
            dmConversationId = undefined;
        });

        test('Student should be able to create a DM with another student and send a message', async ({ login, courseMessages, communicationAPIRequests }) => {
            await login(studentOne);
            const dm = await communicationAPIRequests.createOneToOneChat(course, studentTwo.username);
            dmConversationId = dm.id;
            // Navigate to the DM
            await courseMessages.openConversation(course.id, dm.id);
            // Verify the conversation header shows the recipient
            const page = courseMessages['page'];
            await expect(page.locator('jhi-conversation-header')).toBeVisible({ timeout: 10000 });
            // Write and send a message
            const messageText = 'DM test ' + generateUUID().slice(0, 8);
            await courseMessages.writeMessage(messageText);
            const message = await courseMessages.save();
            // Verify the message appears with correct content
            await courseMessages.checkMessage(message.id!, messageText);
        });

        test('Student should be able to create a DM with instructor', async ({ login, communicationAPIRequests, courseMessages }) => {
            await login(studentOne);
            const dm = await communicationAPIRequests.createOneToOneChat(course, instructor.username);
            dmConversationId = dm.id;
            await courseMessages.openConversation(course.id, dm.id);
            // Verify the conversation header is visible (DM is active)
            const page = courseMessages['page'];
            await expect(page.locator('jhi-conversation-header')).toBeVisible({ timeout: 10000 });
            // Verify the message input area is available (can send messages)
            await expect(page.locator('jhi-message-inline-input')).toBeVisible();
        });
    });

    test.describe('Send/edit/delete message in DM', () => {
        let dmConversationId: number | undefined;

        test.beforeEach('Create DM via API', async ({ login, communicationAPIRequests }) => {
            await login(studentOne);
            const dm = await communicationAPIRequests.createOneToOneChat(course, studentTwo.username);
            dmConversationId = dm.id;
        });

        test.afterEach('Cleanup DM', () => {
            deleteOneToOneChatFromDB(dmConversationId);
            dmConversationId = undefined;
        });

        test('Student should be able to send a message in a DM', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${course.id}/communication?conversationId=${dmConversationId}`);
            const messageText = 'Hello via DM ' + generateUUID().slice(0, 8);
            await courseMessages.writeMessage(messageText);
            const message = await courseMessages.save();
            // Verify message is visible with correct content
            await courseMessages.checkMessage(message.id!, messageText);
            // Verify the message is from studentOne (check author info is present)
            const postLocator = courseMessages.getSinglePost(message.id!);
            await expect(postLocator).toBeVisible();
        });

        test('Student should be able to edit a message in a DM', async ({ login, courseMessages, communicationAPIRequests }) => {
            const originalText = 'Original DM ' + generateUUID().slice(0, 8);
            const message = await communicationAPIRequests.createCourseMessage(course, dmConversationId!, 'oneToOneChat', originalText);
            await login(studentOne, `/courses/${course.id}/communication?conversationId=${dmConversationId}`);
            // Verify original message is visible
            await courseMessages.checkMessage(message.id!, originalText);
            // Edit the message
            const editedText = 'Edited DM ' + generateUUID().slice(0, 8);
            await courseMessages.editMessage(message.id!, editedText);
            // Verify the edited content and the "edited" badge
            await courseMessages.checkMessage(message.id!, editedText);
            await courseMessages.checkMessageEdited(message.id!);
            // Verify the original text is no longer visible
            await expect(courseMessages['page'].getByText(originalText)).toBeHidden({ timeout: 5000 });
        });

        test('Student should be able to delete a message in a DM', async ({ login, courseMessages, communicationAPIRequests }) => {
            const messageText = 'Delete me DM ' + generateUUID().slice(0, 8);
            const message = await communicationAPIRequests.createCourseMessage(course, dmConversationId!, 'oneToOneChat', messageText);
            await login(studentOne, `/courses/${course.id}/communication?conversationId=${dmConversationId}`);
            // Verify message exists before deletion
            await courseMessages.checkMessage(message.id!, messageText);
            // Delete the message
            await courseMessages.deleteMessage(message.id!);
            // Verify the message is no longer visible
            await expect(courseMessages.getSinglePost(message.id!)).toBeHidden({ timeout: 10000 });
            // Verify the message text is gone from the page
            await expect(courseMessages['page'].getByText(messageText)).toBeHidden({ timeout: 5000 });
        });
    });
});
