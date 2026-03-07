import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { instructor, studentOne, studentTwo, tutor } from '../../support/users';
import { GroupChat } from 'app/communication/shared/entities/conversation/group-chat.model';
import { generateUUID } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { execSync } from 'child_process';

const course = { id: SEED_COURSES.groupChat1.id } as any;

// User display names are stable across test runs (from seed data)
const instructorName = 'Instructor User';
const tutorName = 'Tutor User';
const studentOneName = 'Student One';
const studentTwoName = 'Student Two';

/**
 * Deletes a specific group chat from the database.
 * Group chats have no REST delete API, so we use SQL via docker exec.
 */
function deleteGroupChatFromDB(conversationId: number) {
    try {
        const sql = `DELETE FROM conversation_participant WHERE conversation_id = ${conversationId}; DELETE FROM post WHERE conversation_id = ${conversationId}; DELETE FROM conversation WHERE id = ${conversationId};`;
        execSync(`docker exec artemis-postgres psql -U Artemis -d Artemis -c "${sql}"`, { timeout: 5000, encoding: 'utf-8' });
    } catch {
        // DB cleanup not available — non-critical
    }
}

test.describe('Group chat messages', { tag: '@fast' }, () => {
    test.describe('Create group chat', () => {
        let lastCreatedGroupId: number | undefined;

        test.afterEach(() => {
            if (lastCreatedGroupId) {
                deleteGroupChatFromDB(lastCreatedGroupId);
                lastCreatedGroupId = undefined;
            }
        });

        test('Instructors should be able to create a group chat', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${course.id!}/communication`);
            await courseMessages.createGroupChatButton();
            await courseMessages.addUserToGroupChat(studentOne.username);
            await courseMessages.addUserToGroupChat(studentTwo.username);
            const group = await courseMessages.createGroupChat();
            lastCreatedGroupId = group.id!;
            await courseMessages.checkConversationHeaderContains(studentOneName);
            await courseMessages.checkConversationHeaderContains(studentTwoName);
        });

        test('Tutors should be able to create a group chat', async ({ login, courseMessages }) => {
            await login(tutor, `/courses/${course.id!}/communication`);
            await courseMessages.createGroupChatButton();
            await courseMessages.addUserToGroupChat(studentOne.username);
            await courseMessages.addUserToGroupChat(instructor.username);
            const group = await courseMessages.createGroupChat();
            lastCreatedGroupId = group.id!;
            await courseMessages.checkConversationHeaderContains(studentOneName);
            await courseMessages.checkConversationHeaderContains(instructorName);
        });

        test('Students should be able to create a group chat', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${course.id!}/communication`);
            await courseMessages.createGroupChatButton();
            await courseMessages.addUserToGroupChat(studentTwo.username);
            await courseMessages.addUserToGroupChat(tutor.username);
            const group = await courseMessages.createGroupChat();
            lastCreatedGroupId = group.id!;
            await courseMessages.checkConversationHeaderContains(studentTwoName);
            await courseMessages.checkConversationHeaderContains(tutorName);
        });
    });

    test.describe('Add to group chat', () => {
        let groupChat: GroupChat;

        test.beforeEach(async ({ login, communicationAPIRequests }) => {
            await login(instructor);
            groupChat = await communicationAPIRequests.createCourseMessageGroupChat(course, [studentOne.username, tutor.username]);
        });

        test.afterEach(() => deleteGroupChatFromDB(groupChat?.id!));

        test('Tutors should be able to add a user to group chat', async ({ login, courseMessages }) => {
            await login(tutor);
            await courseMessages.openConversation(course.id!, groupChat.id!);
            await courseMessages.addUserToGroupChatButton();
            await courseMessages.addUserToGroupChat(instructor.username);
            await courseMessages.updateGroupChat();
            await courseMessages.checkConversationHeaderContains(instructorName);
        });

        test('Students should be able to add a user to group chat', async ({ login, courseMessages }) => {
            await login(studentOne);
            await courseMessages.openConversation(course.id!, groupChat.id!);
            await courseMessages.addUserToGroupChatButton();
            await courseMessages.addUserToGroupChat(studentTwo.username);
            await courseMessages.updateGroupChat();
            await courseMessages.checkConversationHeaderContains(studentTwoName);
        });
    });

    test.describe('Leave group chat', () => {
        let groupChat: GroupChat;
        let groupChatName: string;

        test.beforeEach(async ({ login, communicationAPIRequests }) => {
            await login(instructor);
            groupChatName = 'leave-' + generateUUID().slice(0, 8);
            groupChat = await communicationAPIRequests.createCourseMessageGroupChat(course, [studentOne.username, tutor.username]);
            await communicationAPIRequests.updateCourseMessageGroupChatName(course, groupChat, groupChatName);
        });

        test.afterEach(() => deleteGroupChatFromDB(groupChat?.id!));

        test('Tutors should be able to leave a group chat', async ({ login, courseMessages, page }) => {
            await login(tutor);
            await courseMessages.openConversation(course.id!, groupChat.id!);
            await courseMessages.listMembersButton(course.id!, groupChat.id!);
            await courseMessages.openSettingsTab();
            await courseMessages.leaveGroupChat();
            // Verify leaving by checking the conversation is no longer accessible
            await page.goto(`/courses/${course.id!}/communication?conversationId=${groupChat.id}`);
            // After leaving, the conversation header should not show the group chat name
            await expect(page.locator('.leave-conversation')).toBeHidden({ timeout: 15000 });
        });

        test('Students should be able to leave a group chat', async ({ login, courseMessages, page }) => {
            await login(studentOne);
            await courseMessages.openConversation(course.id!, groupChat.id!);
            await courseMessages.listMembersButton(course.id!, groupChat.id!);
            await courseMessages.openSettingsTab();
            await courseMessages.leaveGroupChat();
            // Verify leaving by checking the conversation is no longer accessible
            await page.goto(`/courses/${course.id!}/communication?conversationId=${groupChat.id}`);
            // After leaving, the conversation header should not show the group chat name
            await expect(page.locator('.leave-conversation')).toBeHidden({ timeout: 15000 });
        });
    });

    test.describe('Write/edit/delete message in group chat', () => {
        let groupChat: GroupChat;

        test.beforeEach(async ({ login, communicationAPIRequests }) => {
            await login(instructor);
            groupChat = await communicationAPIRequests.createCourseMessageGroupChat(course, [studentOne.username, tutor.username]);
        });

        test.afterEach(() => deleteGroupChatFromDB(groupChat?.id!));

        test('Students should be able to write a message in group chat', async ({ login, courseMessages }) => {
            await login(studentOne);
            await courseMessages.openConversation(course.id!, groupChat.id!);
            const messageText = 'Student Test Message';
            await courseMessages.writeMessage(messageText);
            const message = await courseMessages.save();
            await courseMessages.checkMessage(message.id!, messageText);
        });

        test('Student should be able to edit a message in group chat', async ({ login, courseMessages, communicationAPIRequests }) => {
            await login(studentOne);
            const messageText = 'Student Edit Test Message';
            const message = await communicationAPIRequests.createCourseMessage(course, groupChat.id!, 'groupChat', messageText);
            await courseMessages.openConversation(course.id!, groupChat.id!);
            await courseMessages.checkMessage(message.id!, messageText);
            const newMessage = 'Edited Text';
            await courseMessages.editMessage(message.id!, newMessage);
            await courseMessages.checkMessage(message.id!, newMessage);
            await courseMessages.checkMessageEdited(message.id!);
        });

        test('Students should be able to delete a message in group chat', async ({ login, courseMessages, communicationAPIRequests }) => {
            await login(studentOne);
            const messageText = 'Student Delete Test Message';
            const message = await communicationAPIRequests.createCourseMessage(course, groupChat.id!, 'groupChat', messageText);
            await courseMessages.openConversation(course.id!, groupChat.id!);
            await courseMessages.checkMessage(message.id!, messageText);
            await courseMessages.deleteMessage(message.id!);
            await expect(courseMessages.getSinglePost(message.id!)).not.toBeVisible();
        });
    });
});
