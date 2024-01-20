import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { Course } from 'app/entities/course.model';
import { admin, instructor, studentOne, studentTwo, tutor, users } from '../../support/users';
import { generateUUID, titleLowercase } from '../../support/utils';
import { Channel } from 'app/entities/metis/conversation/channel.model';
import { GroupChat } from 'app/entities/metis/conversation/group-chat.model';

test.describe('Course messages', () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();

        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
    });

    test('Accepts code of conduct', async ({ login, courseMessages }) => {
        await login(instructor, `/courses/${course.id}/messages`);
        await courseMessages.acceptCodeOfConductButton();
        await login(studentOne, `/courses/${course.id}/messages`);
        await courseMessages.acceptCodeOfConductButton();
        await login(studentTwo, `/courses/${course.id}/messages`);
        await courseMessages.acceptCodeOfConductButton();
        await login(tutor, `/courses/${course.id}/messages`);
        await courseMessages.acceptCodeOfConductButton();
    });

    test.describe('Channel messages', () => {
        test.describe('Create channel', () => {
            test('Check for pre-created channels', async ({ login, courseMessages }) => {
                await login(instructor, `/courses/${course.id}/messages`);
                await courseMessages.browseChannelsButton();
                await courseMessages.checkChannelsExists('tech-support');
                await courseMessages.checkChannelsExists('organization');
                await courseMessages.checkChannelsExists('random');
                await courseMessages.checkChannelsExists('announcement');
            });

            test('Instructors should be able to create a public announcement channel', async ({ login, courseMessages }) => {
                await login(instructor, `/courses/${course.id}/messages`);
                const name = 'public-ancmnt-ch';
                await courseMessages.createChannelButton();
                await courseMessages.setName(name);
                await courseMessages.setDescription('A public announcement channel');
                await courseMessages.setPublic();
                await courseMessages.setAnnouncementChannel();
                await courseMessages.createChannel(true, true);
                await expect(courseMessages.getName()).toContainText(name);
            });

            test('Instructors should be able to create a private announcement channel', async ({ login, courseMessages }) => {
                await login(instructor, `/courses/${course.id}/messages`);
                const name = 'private-ancmnt-ch';
                await courseMessages.createChannelButton();
                await courseMessages.setName(name);
                await courseMessages.setDescription('A private announcement channel');
                await courseMessages.setPrivate();
                await courseMessages.setAnnouncementChannel();
                await courseMessages.createChannel(true, false);
                await expect(courseMessages.getName()).toContainText(name);
            });

            test('Instructors should be able to create a public unrestricted channel', async ({ login, courseMessages }) => {
                await login(instructor, `/courses/${course.id}/messages`);
                const name = 'public-unrstct-ch';
                await courseMessages.createChannelButton();
                await courseMessages.setName(name);
                await courseMessages.setDescription('A public unrestricted channel');
                await courseMessages.setPublic();
                await courseMessages.setUnrestrictedChannel();
                await courseMessages.createChannel(false, true);
                await expect(courseMessages.getName()).toContainText(name);
            });

            test('Instructors should be able to create a private unrestricted channel', async ({ login, courseMessages }) => {
                await login(instructor, `/courses/${course.id}/messages`);
                const name = 'private-unrstct-ch';
                await courseMessages.createChannelButton();
                await courseMessages.setName(name);
                await courseMessages.setDescription('A public unrestricted channel');
                await courseMessages.setPrivate();
                await courseMessages.setUnrestrictedChannel();
                await courseMessages.createChannel(false, false);
                await expect(courseMessages.getName()).toContainText(name);
            });

            test('Instructors should not be able to create a channel with uppercase name', async ({ login, courseMessages }) => {
                await login(instructor, `/courses/${course.id}/messages`);
                const name = 'Forbidden Name';
                await courseMessages.createChannelButton();
                await courseMessages.setName(name);
                await expect(courseMessages.getError()).toContainText('Names can only contain lowercase letters');
            });

            test('Instructors should not be able to create a channel with name longer than 30 chars', async ({ login, courseMessages }) => {
                await login(instructor, `/courses/${course.id}/messages`);
                const name = 'way-way-way-too-long-channel-title';
                await courseMessages.createChannelButton();
                await courseMessages.setName(name);
                await expect(courseMessages.getError()).toContainText('Name can be max 30 characters long!');
            });

            test('Check that channel is created when a lecture is created', async ({ login, courseMessages, courseManagementAPIRequests }) => {
                await login(admin);
                await courseManagementAPIRequests.createLecture(course, 'Test Lecture');
                await login(instructor, `/courses/${course.id}/messages`);
                await courseMessages.browseLectureChannelsButton();
                await courseMessages.checkChannelsExists('lecture-test-lecture');
            });

            test('Check that channel is created when an exercise is created', async ({ login, courseMessages, exerciseAPIRequests }) => {
                await login(admin);
                await exerciseAPIRequests.createTextExercise({ course }, 'Test Exercise');
                await login(instructor, `/courses/${course.id}/messages`);
                await courseMessages.browseExerciseChannelsButton();
                await courseMessages.checkChannelsExists('exercise-test-exercise');
            });

            test('Check that channel is created when an exam is created', async ({ login, courseMessages, examAPIRequests }) => {
                await login(admin);
                const examTitle = 'exam' + generateUUID();
                await examAPIRequests.createExam({ course, title: examTitle });
                await login(instructor, `/courses/${course.id}/messages`);
                await courseMessages.browseExamChannelsButton();
                await courseMessages.checkChannelsExists(titleLowercase(examTitle));
            });
        });

        test.describe('Edit channel', () => {
            let channel: Channel;

            test.beforeEach('Create channel', async ({ login, communicationAPIRequests }) => {
                await login(admin);
                channel = await communicationAPIRequests.createCourseMessageChannel(course, 'test-channel', 'Test Channel', true, true);
                await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, instructor);
            });

            test('Instructors should be able to edit a channel', async ({ login, courseMessages, page }) => {
                await login(instructor, `/courses/${course.id}/messages?conversationId=${channel.id}`);
                const newName = 'new-test-name';
                const topic = 'test-topic';
                await courseMessages.getName().click();
                await courseMessages.editName(newName);
                await courseMessages.editTopic(topic);
                await courseMessages.editDescription('New Description');
                await courseMessages.closeEditPanel();
                await page.reload();
                await expect(courseMessages.getName()).toContainText(newName);
                await expect(courseMessages.getTopic()).toContainText(topic);
            });
        });

        test.describe('Join channel', async () => {
            let channel: Channel;

            test.beforeEach('Create channel', async ({ login, communicationAPIRequests }) => {
                await login(admin);
                channel = await communicationAPIRequests.createCourseMessageChannel(course, 'test-channel', 'Test Channel', true, true);
            });

            test('Student should be joined into pre-created channels automatically', async ({ login, courseMessages }) => {
                await login(studentOne, `/courses/${course.id}/messages`);
                await courseMessages.browseChannelsButton();
                const techSupportChannelId = Number(await courseMessages.getChannelIdByName('tech-support'));
                await expect(courseMessages.checkBadgeJoined(techSupportChannelId)).toContainText('Joined');

                const randomChannelId = Number(await courseMessages.getChannelIdByName('random'));
                await expect(courseMessages.checkBadgeJoined(randomChannelId)).toContainText('Joined');

                const announcementChannelId = Number(await courseMessages.getChannelIdByName('announcement'));
                await expect(courseMessages.checkBadgeJoined(announcementChannelId)).toContainText('Joined');

                const organizationChannelId = Number(await courseMessages.getChannelIdByName('organization'));
                await expect(courseMessages.checkBadgeJoined(organizationChannelId)).toContainText('Joined');
            });

            test('Student should be able to join a public channel', async ({ login, courseMessages }) => {
                await login(studentOne, `/courses/${course.id}/messages`);
                await courseMessages.browseChannelsButton();
                await courseMessages.joinChannel(channel.id!);
                await expect(courseMessages.checkBadgeJoined(channel.id!)).toContainText('Joined');
            });

            test('Student should be able to leave a public channel', async ({ login, courseMessages }) => {
                await login(studentOne, `/courses/${course.id}/messages`);
                await courseMessages.browseChannelsButton();
                await courseMessages.leaveChannel(channel.id!);
                await expect(courseMessages.checkBadgeJoined(channel.id!)).toHaveCount(0);
            });
        });

        test.describe('Write/edit/delete message in channel', () => {
            let channel: Channel;

            test.beforeEach('Create channel', async ({ login, communicationAPIRequests }) => {
                await login(admin);
                channel = await communicationAPIRequests.createCourseMessageChannel(course, 'test-channel', 'Test Channel', true, true);
                await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, instructor);
            });

            test('student should be able to write message in channel', async ({ login, courseMessages, communicationAPIRequests }) => {
                await login(studentOne, `/courses/${course.id}/messages?conversationId=${channel.id}`);
                const messageText = 'Student Test Message';
                await courseMessages.writeMessage(messageText);
                const message = await communicationAPIRequests.createCourseMessage(course, channel.id!, 'channel', messageText);
                await courseMessages.checkMessage(message.id!, messageText);
            });

            test('student should be able to edit message in channel', async ({ login, courseMessages, communicationAPIRequests }) => {
                await login(studentOne, `/courses/${course.id}/messages?conversationId=${channel.id!}`);
                const messageText = 'Student Edit Test Message';
                const message = await communicationAPIRequests.createCourseMessage(course, channel.id!, 'channel', messageText);
                const newMessage = 'Edited Text';
                await courseMessages.editMessage(message.id!, newMessage);
                await courseMessages.checkMessage(message.id!, newMessage);
                await expect(courseMessages.getSinglePost(message.id!).locator('.edited-text')).toBeVisible();
            });

            test('student should be able to delete message in channel', async ({ login, courseMessages, communicationAPIRequests }) => {
                await login(studentOne, `/courses/${course.id}/messages?conversationId=${channel.id}`);
                const messageText = 'Student Edit Test Message';
                const message = await communicationAPIRequests.createCourseMessage(course, channel.id!, 'channel', messageText);
                await courseMessages.checkMessage(message.id!, messageText);
                await courseMessages.deleteMessage(message.id!);
                await expect(courseMessages.getSinglePost(message.id!)).not.toBeVisible();
            });
        });
    });

    test.describe('Group chats', () => {
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
                await login(instructor, `/courses/${course.id}/messages`);
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
                await login(tutor, `/courses/${course.id}/messages`);
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
                await login(studentOne, `/courses/${course.id}/messages`);
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
                await login(admin);
                groupChat = await communicationAPIRequests.createCourseMessageGroupChat(course, [studentOne.username, tutor.username]);
            });

            test('Tutors should be able to add a user to group chat', async ({ login, courseMessages }) => {
                await login(tutor, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                await courseMessages.addUserToGroupChatButton();
                await courseMessages.addUserToGroupChat(instructor.username);
                await courseMessages.updateGroupChat();

                await courseMessages.listMembersButton(course.id!, groupChat.id!);
                await courseMessages.checkMemberList(instructorName);
                await courseMessages.closeEditPanel();
            });

            test('Students should be able to add a user to group chat', async ({ login, courseMessages }) => {
                await login(studentOne, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
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
            const groupChatName = 'leave-test';

            test.beforeEach(async ({ login, communicationAPIRequests }) => {
                await login(admin);
                groupChat = await communicationAPIRequests.createCourseMessageGroupChat(course, [studentOne.username, tutor.username]);
                await communicationAPIRequests.updateCourseMessageGroupChatName(course, groupChat, groupChatName);
            });

            test('Tutors should be able to leave a group chat', async ({ login, courseMessages, page }) => {
                await login(tutor, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                await courseMessages.checkGroupChatExists(groupChatName, true);
                await courseMessages.listMembersButton(course.id!, groupChat.id!);
                await courseMessages.openSettingsTab();
                await courseMessages.leaveGroupChat();
                await page.goto(`/courses/${course.id}/messages`);
                await courseMessages.checkGroupChatExists(groupChatName, false);
            });

            test('Students should be able to leave a group chat', async ({ login, courseMessages, page }) => {
                await login(studentOne, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                await courseMessages.checkGroupChatExists(groupChatName, true);
                await courseMessages.listMembersButton(course.id!, groupChat.id!);
                await courseMessages.openSettingsTab();
                await courseMessages.leaveGroupChat();
                await page.goto(`/courses/${course.id}/messages`);
                await courseMessages.checkGroupChatExists(groupChatName, false);
            });
        });

        test.describe('Write/edit/delete message in group chat', () => {
            let groupChat: GroupChat;

            test.beforeEach(async ({ login, communicationAPIRequests }) => {
                await login(admin);
                groupChat = await communicationAPIRequests.createCourseMessageGroupChat(course, [studentOne.username, tutor.username]);
            });

            test('Students should be able to write a message in group chat', async ({ login, courseMessages }) => {
                await login(studentOne, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                const messageText = 'Student Test Message';
                await courseMessages.writeMessage(messageText);
                const message = await courseMessages.save(true);
                await courseMessages.checkMessage(message.id!, messageText);
            });

            test('Student should be able to edit a message in group chat', async ({ login, courseMessages, communicationAPIRequests }) => {
                await login(studentOne, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                const messageText = 'Student Edit Test Message';
                const message = await communicationAPIRequests.createCourseMessage(course, groupChat.id!, 'groupChat', messageText);
                const newMessage = 'Edited Text';
                await courseMessages.editMessage(message.id!, newMessage);
                await courseMessages.checkMessage(message.id!, newMessage);
                await expect(courseMessages.getSinglePost(message.id!).locator('.edited-text')).toBeVisible();
            });

            test('Students should be able to delete a message in group chat', async ({ login, courseMessages, communicationAPIRequests }) => {
                await login(studentOne, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                const messageText = 'Student Edit Test Message';
                const message = await communicationAPIRequests.createCourseMessage(course, groupChat.id!, 'groupChat', messageText);
                await courseMessages.checkMessage(message.id!, messageText);
                await courseMessages.deleteMessage(message.id!);
                await expect(courseMessages.getSinglePost(message.id!)).not.toBeVisible();
            });
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
