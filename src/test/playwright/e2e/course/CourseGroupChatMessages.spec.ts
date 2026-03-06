import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentTwo, tutor, users } from '../../support/users';
import { GroupChat } from 'app/communication/shared/entities/conversation/group-chat.model';
import { generateUUID } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';

const course = { id: SEED_COURSES.groupChat1.id } as any;

test.describe('Group chat messages', { tag: '@fast' }, () => {
    let instructorName: string;
    let tutorName: string;
    let studentOneName: string;
    let studentTwoName: string;

    test.beforeEach('Get usernames', async ({ login, page }) => {
        await login(admin);
        const instructorInfo = await users.getUserInfo(instructor.username, page);
        instructorName = instructorInfo.name!;
        const tutorInfo = await users.getUserInfo(tutor.username, page);
        tutorName = tutorInfo.name!;
        const studentOneInfo = await users.getUserInfo(studentOne.username, page);
        studentOneName = studentOneInfo.name!;
        const studentTwoInfo = await users.getUserInfo(studentTwo.username, page);
        studentTwoName = studentTwoInfo.name!;
    });

    test.describe('Create group chat', () => {
        test('Instructors should be able to create a group chat', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${course.id!}/communication`);
            await courseMessages.createGroupChatButton();
            await courseMessages.addUserToGroupChat(studentOne.username);
            await courseMessages.addUserToGroupChat(studentTwo.username);
            const group = await courseMessages.createGroupChat();
            await courseMessages.listMembersButton(course.id!, group.id!);
            await courseMessages.checkMemberList(studentOneName);
            await courseMessages.checkMemberList(studentTwoName);
            await courseMessages.closeEditPanel();
        });

        test('Tutors should be able to create a group chat', async ({ login, courseMessages }) => {
            await login(tutor, `/courses/${course.id!}/communication`);
            await courseMessages.createGroupChatButton();
            await courseMessages.addUserToGroupChat(studentOne.username);
            await courseMessages.addUserToGroupChat(instructor.username);
            const group = await courseMessages.createGroupChat();
            await courseMessages.listMembersButton(course.id!, group.id!);
            await courseMessages.checkMemberList(studentOneName);
            await courseMessages.checkMemberList(instructorName);
            await courseMessages.closeEditPanel();
        });

        test('Students should be able to create a group chat', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${course.id!}/communication`);
            await courseMessages.createGroupChatButton();
            await courseMessages.addUserToGroupChat(studentTwo.username);
            await courseMessages.addUserToGroupChat(tutor.username);
            const group = await courseMessages.createGroupChat();
            await courseMessages.listMembersButton(course.id!, group.id!);
            await courseMessages.checkMemberList(studentTwoName);
            await courseMessages.checkMemberList(tutorName);
            await courseMessages.closeEditPanel();
        });
    });

    test.describe('Add to group chat', () => {
        let groupChat: GroupChat;

        test.beforeEach(async ({ login, communicationAPIRequests }) => {
            await login(instructor);
            groupChat = await communicationAPIRequests.createCourseMessageGroupChat(course, [studentOne.username, tutor.username]);
        });

        test('Tutors should be able to add a user to group chat', async ({ login, courseMessages }) => {
            await login(tutor, `/courses/${course.id!}/communication?conversationId=${groupChat.id}`);
            await courseMessages.addUserToGroupChatButton();
            await courseMessages.addUserToGroupChat(instructor.username);
            await courseMessages.updateGroupChat();

            await courseMessages.listMembersButton(course.id!, groupChat.id!);
            await courseMessages.checkMemberList(instructorName);
            await courseMessages.closeEditPanel();
        });

        test('Students should be able to add a user to group chat', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${course.id!}/communication?conversationId=${groupChat.id}`);
            await courseMessages.addUserToGroupChatButton();
            await courseMessages.addUserToGroupChat(studentTwo.username);
            await courseMessages.updateGroupChat();

            await courseMessages.listMembersButton(course.id!, groupChat.id!);
            await courseMessages.checkMemberList(studentTwoName);
            await courseMessages.closeEditPanel();
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

        test('Tutors should be able to leave a group chat', async ({ login, courseMessages, page }) => {
            await login(tutor, `/courses/${course.id!}/communication?conversationId=${groupChat.id}`);
            await courseMessages.listMembersButton(course.id!, groupChat.id!);
            await courseMessages.openSettingsTab();
            await courseMessages.leaveGroupChat();
            // Verify leaving by checking the conversation is no longer accessible
            await page.goto(`/courses/${course.id!}/communication?conversationId=${groupChat.id}`);
            // After leaving, the conversation header should not show the group chat name
            await expect(page.locator('.leave-conversation')).toBeHidden({ timeout: 15000 });
        });

        test('Students should be able to leave a group chat', async ({ login, courseMessages, page }) => {
            await login(studentOne, `/courses/${course.id!}/communication?conversationId=${groupChat.id}`);
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

        test('Students should be able to write a message in group chat', async ({ login, courseMessages, page }) => {
            await login(studentOne, `/courses/${course.id!}/communication?conversationId=${groupChat.id}`);
            // Wait for the conversation to be loaded (members button appears when conversation is active)
            await page.locator('.members').waitFor({ state: 'visible', timeout: 30000 });
            const messageText = 'Student Test Message';
            await courseMessages.writeMessage(messageText);
            const message = await courseMessages.save();
            await courseMessages.checkMessage(message.id!, messageText);
        });

        test('Student should be able to edit a message in group chat', async ({ login, courseMessages, page }) => {
            await login(studentOne, `/courses/${course.id!}/communication?conversationId=${groupChat.id}`);
            await page.locator('.members').waitFor({ state: 'visible', timeout: 30000 });
            const messageText = 'Student Edit Test Message';
            await courseMessages.writeMessage(messageText);
            const message = await courseMessages.save();
            await courseMessages.checkMessage(message.id!, messageText);
            const newMessage = 'Edited Text';
            await courseMessages.editMessage(message.id!, newMessage);
            await courseMessages.checkMessage(message.id!, newMessage);
            await courseMessages.checkMessageEdited(message.id!);
        });

        test('Students should be able to delete a message in group chat', async ({ login, courseMessages, page }) => {
            await login(studentOne, `/courses/${course.id!}/communication?conversationId=${groupChat.id}`);
            await page.locator('.members').waitFor({ state: 'visible', timeout: 30000 });
            const messageText = 'Student Delete Test Message';
            await courseMessages.writeMessage(messageText);
            const message = await courseMessages.save();
            await courseMessages.checkMessage(message.id!, messageText);
            await courseMessages.deleteMessage(message.id!);
            await expect(courseMessages.getSinglePost(message.id!)).not.toBeVisible();
        });
    });
});
