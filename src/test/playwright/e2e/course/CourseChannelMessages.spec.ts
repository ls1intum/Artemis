import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { generateUUID, titleLowercase } from '../../support/utils';
import { Channel } from 'app/communication/shared/entities/conversation/channel.model';
import { SEED_COURSES } from '../../support/seedData';

// Use pre-seeded courses — no course creation needed
const readOnlyCourse = { id: SEED_COURSES.channel1.id };
const writeCourse = { id: SEED_COURSES.channel2.id };

test.describe('Channel messages', { tag: '@fast' }, () => {
    test.describe('Create channel', () => {
        test('Check for pre-created channels', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${readOnlyCourse.id}/communication`);
            await courseMessages.browseChannelsButton();
            await courseMessages.checkChannelsExists('tech-support');
            await courseMessages.checkChannelsExists('organization');
            await courseMessages.checkChannelsExists('random');
            await courseMessages.checkChannelsExists('announcement');
        });

        test('Instructor should be able to create a public announcement channel', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${writeCourse.id}/communication`);
            const name = 'pub-ann-' + generateUUID().slice(0, 8);
            await courseMessages.createChannelButton();
            await courseMessages.setName(name);
            await courseMessages.setDescription('A public announcement channel');
            await courseMessages.setPublic();
            await courseMessages.setAnnouncementChannel();
            await courseMessages.createChannel(true, true);
            await expect(courseMessages.getName()).toContainText(name);
        });

        test('Instructor should be able to create a private announcement channel', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${writeCourse.id}/communication`);
            const name = 'prv-ann-' + generateUUID().slice(0, 8);
            await courseMessages.createChannelButton();
            await courseMessages.setName(name);
            await courseMessages.setDescription('A private announcement channel');
            await courseMessages.setPrivate();
            await courseMessages.setAnnouncementChannel();
            await courseMessages.createChannel(true, false);
            await expect(courseMessages.getName()).toContainText(name);
        });

        test('Instructor should be able to create a public unrestricted channel', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${writeCourse.id}/communication`);
            const name = 'pub-unr-' + generateUUID().slice(0, 8);
            await courseMessages.createChannelButton();
            await courseMessages.setName(name);
            await courseMessages.setDescription('A public unrestricted channel');
            await courseMessages.setPublic();
            await courseMessages.setUnrestrictedChannel();
            await courseMessages.createChannel(false, true);
            await expect(courseMessages.getName()).toContainText(name);
        });

        test('Instructor should be able to create a public course-wide unrestricted channel', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${writeCourse.id}/communication`);
            const name = 'pub-cw-' + generateUUID().slice(0, 8);
            await courseMessages.createChannelButton();
            await courseMessages.setName(name);
            await courseMessages.setDescription('A public unrestricted channel');
            await courseMessages.setPublic();
            await courseMessages.setUnrestrictedChannel();
            await courseMessages.setCourseWideChannel();
            await courseMessages.createChannel(false, true);
            await expect(courseMessages.getName()).toContainText(name);
        });

        test('Instructor should be able to create a private unrestricted channel', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${writeCourse.id}/communication`);
            const name = 'prv-unr-' + generateUUID().slice(0, 8);
            await courseMessages.createChannelButton();
            await courseMessages.setName(name);
            await courseMessages.setDescription('A public unrestricted channel');
            await courseMessages.setPrivate();
            await courseMessages.setUnrestrictedChannel();
            await courseMessages.createChannel(false, false);
            await expect(courseMessages.getName()).toContainText(name);
        });

        test('Instructor should not be able to create a channel with uppercase name', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${writeCourse.id}/communication`);
            const name = 'Forbidden Name';
            await courseMessages.createChannelButton();
            await courseMessages.setName(name);
            await expect(courseMessages.getError()).toContainText('Names can only contain lowercase letters');
        });

        test('Instructor should not be able to create a channel with name longer than 20 chars', async ({ login, courseMessages }) => {
            await login(instructor, `/courses/${writeCourse.id}/communication`);
            const name = 'way-way-way-too-long-channel-title';
            await courseMessages.createChannelButton();
            await courseMessages.setName(name);
            await expect(courseMessages.getError()).toContainText('Name can be max 20 characters long!');
        });

        test('Check that channel is created when a lecture is created', async ({ login, courseMessages, courseManagementAPIRequests }) => {
            await login(admin);
            const uid = generateUUID().slice(0, 6);
            await courseManagementAPIRequests.createLecture({ id: writeCourse.id } as any, uid);
            await login(instructor, `/courses/${writeCourse.id}/communication`);
            await courseMessages.browseChannelsButton();
            await courseMessages.checkChannelsExists('lecture-' + uid);
        });

        test('Check that channel is created when an exercise is created', async ({ login, courseMessages, exerciseAPIRequests }) => {
            await login(admin);
            const uid = generateUUID().slice(0, 6);
            await exerciseAPIRequests.createTextExercise({ course: { id: writeCourse.id } as any }, uid);
            await login(instructor, `/courses/${writeCourse.id}/communication`);
            await courseMessages.browseChannelsButton();
            await courseMessages.checkChannelsExists('exercise-' + uid);
        });

        test('Check that channel is created when an exam is created', async ({ login, courseMessages, examAPIRequests }) => {
            await login(admin);
            const examTitle = 'exam' + generateUUID();
            await examAPIRequests.createExam({ course: { id: writeCourse.id } as any, title: examTitle });
            await login(instructor, `/courses/${writeCourse.id}/communication`);
            await courseMessages.browseChannelsButton();
            await courseMessages.checkChannelsExists(titleLowercase(examTitle));
        });
    });

    test.describe('Edit channel', () => {
        let channel: Channel;

        test.beforeEach('Create channel', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            const channelName = 'edit-ch-' + generateUUID().slice(0, 8);
            channel = await communicationAPIRequests.createCourseMessageChannel({ id: writeCourse.id } as any, channelName, 'Test Channel', true, true);
            await communicationAPIRequests.joinUserIntoChannel({ id: writeCourse.id } as any, channel.id!, instructor);
        });

        test('Instructor should be able to edit a channel', async ({ login, courseMessages, page }) => {
            await login(instructor, `/courses/${writeCourse.id}/communication?conversationId=${channel.id}`);
            const newName = 'new-' + generateUUID().slice(0, 8);
            const topic = 'test-topic';

            await courseMessages.editName(newName);
            await courseMessages.editTopic(topic);
            await courseMessages.editDescription('New Description');

            await page.reload();
            await page.locator('jhi-conversation-header').waitFor({ state: 'visible', timeout: 10000 });
            await expect(courseMessages.getName()).toContainText(newName);
            await expect(courseMessages.getTopic()).toContainText(topic);
        });
    });

    test.describe('Join channel', async () => {
        let channel: Channel;

        test.beforeEach('Create channel', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            const channelName = 'join-ch-' + generateUUID().slice(0, 8);
            channel = await communicationAPIRequests.createCourseMessageChannel({ id: readOnlyCourse.id } as any, channelName, 'Test Channel', true, true);
        });

        test('Student should be joined into pre-created channels automatically', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${readOnlyCourse.id}/communication`);
            await courseMessages.browseChannelsButton();
            const techSupportChannelId = Number(await courseMessages.getChannelIdByName('tech-support'));
            const techSupportJoinedBadge = courseMessages.getJoinedBadge(techSupportChannelId);
            await expect(techSupportJoinedBadge).toBeVisible();
            await expect(techSupportJoinedBadge).toContainText('Joined');

            const randomChannelId = Number(await courseMessages.getChannelIdByName('random'));
            const randomJoinedBadge = courseMessages.getJoinedBadge(randomChannelId);
            await expect(randomJoinedBadge).toBeVisible();
            await expect(randomJoinedBadge).toContainText('Joined');

            const announcementChannelId = Number(await courseMessages.getChannelIdByName('announcement'));
            const announcementJoinedBadge = courseMessages.getJoinedBadge(announcementChannelId);
            await expect(announcementJoinedBadge).toBeVisible();
            await expect(announcementJoinedBadge).toContainText('Joined');

            const organizationChannelId = Number(await courseMessages.getChannelIdByName('organization'));
            const organizationJoinedBadge = courseMessages.getJoinedBadge(organizationChannelId);
            await expect(organizationJoinedBadge).toBeVisible();
            await expect(organizationJoinedBadge).toContainText('Joined');
        });

        test('Student should be able to join a public channel', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${readOnlyCourse.id}/communication`);
            await courseMessages.browseChannelsButton();
            await courseMessages.joinChannel(channel.id!);
            const joinedBadge = courseMessages.getJoinedBadge(channel.id!);
            await expect(joinedBadge).toBeVisible();
            await expect(joinedBadge).toContainText('Joined');
        });

        test('Student should be able to leave a public channel', async ({ login, courseMessages, communicationAPIRequests }) => {
            await login(studentOne, `/courses/${readOnlyCourse.id}/communication`);
            await communicationAPIRequests.joinUserIntoChannel({ id: readOnlyCourse.id } as any, channel.id!, studentOne);
            await courseMessages.browseChannelsButton();
            await courseMessages.leaveChannel(channel.id!);
            await expect(courseMessages.getJoinedBadge(channel.id!)).toBeHidden();
        });
    });

    test.describe('Write/edit/delete message in channel', () => {
        let channel: Channel;

        test.beforeEach('Create channel', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            const channelName = 'msg-ch-' + generateUUID().slice(0, 8);
            channel = await communicationAPIRequests.createCourseMessageChannel({ id: writeCourse.id } as any, channelName, 'Test Channel', false, true);
            await communicationAPIRequests.joinUserIntoChannel({ id: writeCourse.id } as any, channel.id!, studentOne);
        });

        test('Student should be able to write message in channel', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${channel.id}`);
            const messageText = 'Student Test Message';
            await courseMessages.writeMessage(messageText);
            const message = await courseMessages.save();
            await courseMessages.checkMessage(message.id!, messageText);
        });

        test('Student should be able to edit message in channel', async ({ login, courseMessages, communicationAPIRequests }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${channel.id!}`);
            const messageText = 'Student Edit Test Message';
            const message = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, channel.id!, 'channel', messageText);
            const newMessage = 'Edited Text';
            await courseMessages.editMessage(message.id!, newMessage);
            await courseMessages.checkMessage(message.id!, newMessage);
            await expect(courseMessages.getSinglePost(message.id!).locator('.edited-text')).toBeVisible();
        });

        test('Student should be able to delete message in channel', async ({ login, courseMessages, communicationAPIRequests }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${channel.id}`);
            const messageText = 'Student Edit Test Message';
            const message = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, channel.id!, 'channel', messageText);
            await courseMessages.checkMessage(message.id!, messageText);
            await courseMessages.deleteMessage(message.id!);
            await expect(courseMessages.getSinglePost(message.id!)).not.toBeVisible();
        });
    });
});
