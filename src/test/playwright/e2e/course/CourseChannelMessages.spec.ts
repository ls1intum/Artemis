import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne } from '../../support/users';
import { generateUUID, titleLowercase } from '../../support/utils';
import { Channel } from 'app/communication/shared/entities/conversation/channel.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
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

    test.describe('Infinite scroll in channel messages', () => {
        let channel: Channel;
        let oldestPost: Post;
        let newestPost: Post;
        // The message list loads 50 posts per page. Seeding three pages' worth puts the oldest post on the
        // THIRD page, so it can only appear after two successive scroll-ups — which is what exercises (and
        // regression-guards) chained paging in the in-house infinite-scroll directive: an earlier version
        // loaded the first older page but then stalled, because the post-load scroll nudge left the sentinel
        // inside the prefetch zone and produced no further IntersectionObserver callback.
        const messagesToSeed = 120;

        test.beforeEach('Create channel with three pages of messages', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            const channelName = 'inf-scroll-' + generateUUID().slice(0, 8);
            channel = await communicationAPIRequests.createCourseMessageChannel({ id: writeCourse.id } as any, channelName, 'Infinite scroll channel', false, true);
            await communicationAPIRequests.joinUserIntoChannel({ id: writeCourse.id } as any, channel.id!, studentOne);

            // Seed the messages as the student who will view them: a user's own messages are not "unread", so the
            // conversation deterministically scrolls to the bottom on open (the newest page) instead of jumping to
            // the first unread post, which would otherwise auto-load earlier pages and make the assertions flaky.
            await login(studentOne);

            // Posted (and awaited) first, so it is guaranteed to be the oldest post and land on the last page.
            oldestPost = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, channel.id!, 'channel', 'Oldest infinite scroll message');
            // Filler messages in between, posted in small parallel batches; their relative order is irrelevant
            // as long as they are newer than the oldest and older than the newest.
            const fillerCount = messagesToSeed - 2;
            for (let batchStart = 0; batchStart < fillerCount; batchStart += 20) {
                await Promise.all(
                    Array.from({ length: Math.min(20, fillerCount - batchStart) }, (_, index) =>
                        communicationAPIRequests.createCourseMessage(
                            { id: writeCourse.id } as any,
                            channel.id!,
                            'channel',
                            `Infinite scroll filler message ${batchStart + index + 1}`,
                        ),
                    ),
                );
            }
            // Posted (and awaited) last, so it is guaranteed to be the newest post and land on the first page.
            newestPost = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, channel.id!, 'channel', 'Newest infinite scroll message');

            // Mark the conversation as read so it has no unread messages: otherwise the conversation may jump to
            // the first unread post on open and auto-load earlier pages, making "oldest absent initially" flaky.
            await communicationAPIRequests.markConversationAsRead(writeCourse.id, channel.id!);
        });

        test('Scrolling up repeatedly chain-loads earlier pages of older messages', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication?conversationId=${channel.id}`);

            // The conversation loads the newest page and scrolls to the bottom, so the newest post is shown.
            await courseMessages.checkMessage(newestPost.id!, 'Newest infinite scroll message');

            // The oldest post sits on the earliest page, several pages above the newest. Scroll to the top
            // repeatedly to chain-load earlier pages until it is rendered: each scroll-to-top must load one more
            // page. The pre-fix directive stalled after the first older page (the post-load scroll nudge left the
            // sentinel inside the prefetch zone, so no further IntersectionObserver callback fired), and the oldest
            // post never appeared — this loop would then time out. We do not assert the exact initial page count
            // because the conversation's initial load is not deterministic, but reaching the oldest post still
            // requires the directive to keep paging across successive scroll-ups.
            await expect(async () => {
                await courseMessages.scrollMessagesToTop();
                expect(await courseMessages.getSinglePost(oldestPost.id!).count()).toBe(1);
            }).toPass({ timeout: 30000, intervals: [700, 1000, 1000] });

            await courseMessages.checkMessage(oldestPost.id!, 'Oldest infinite scroll message');
        });
    });

    test.describe('Infinite scroll in the exercise discussion section', () => {
        let exercise: TextExercise;
        let oldestPost: Post;
        let newestPost: Post;
        // The discussion section reuses the same infinite-scroll directive, so seed three pages here too and
        // verify chained paging works in this (different) consumer, which embeds the directive on the exercise page.
        const messagesToSeed = 120;

        test.beforeEach('Create an exercise channel with three pages of messages', async ({ page, login, exerciseAPIRequests, communicationAPIRequests }) => {
            await login(admin);
            // Keep the title short: the auto-created exercise channel is named `exercise-<title>` and channel
            // names are capped at 20 characters, so a long title would prevent the channel from being created.
            exercise = await exerciseAPIRequests.createTextExercise({ course: { id: writeCourse.id } as any }, generateUUID().slice(0, 8));
            // The exercise's channel is created asynchronously, so under load it may not exist the instant the
            // exercise POST returns. Poll until it is available before posting messages to it.
            let channel = await communicationAPIRequests.getExerciseChannel(writeCourse.id, exercise.id!);
            for (let attempt = 0; attempt < 20 && !channel?.id; attempt++) {
                await page.waitForTimeout(500);
                channel = await communicationAPIRequests.getExerciseChannel(writeCourse.id, exercise.id!);
            }
            expect(channel?.id, 'exercise channel should be created').toBeTruthy();
            await communicationAPIRequests.joinUserIntoChannel({ id: writeCourse.id } as any, channel.id!, studentOne);

            oldestPost = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, channel.id!, 'channel', 'Oldest discussion infinite scroll message');
            const fillerCount = messagesToSeed - 2;
            for (let batchStart = 0; batchStart < fillerCount; batchStart += 20) {
                await Promise.all(
                    Array.from({ length: Math.min(20, fillerCount - batchStart) }, (_, index) =>
                        communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, channel.id!, 'channel', `Discussion filler message ${batchStart + index + 1}`),
                    ),
                );
            }
            newestPost = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, channel.id!, 'channel', 'Newest discussion infinite scroll message');
        });

        test('Scrolling up repeatedly chain-loads earlier pages in the exercise discussion section', async ({ login, courseCommunication }) => {
            await login(studentOne, `/courses/${writeCourse.id}/exercises/${exercise.id}`);

            // The discussion section loads the newest page and scrolls to the bottom, so the newest post is shown.
            await courseCommunication.checkDiscussionPost(newestPost.id!, 'Newest discussion infinite scroll message');

            // The oldest post sits on the earliest page. Scroll to the top repeatedly to chain-load earlier pages
            // until it is rendered: the pre-fix directive stalled after the first older page and never reached it,
            // so this loop would time out. We do not assert the exact initial page count because the discussion
            // section's initial load is not deterministic (it differs in a multi-node setup), but reaching the
            // oldest post still requires the directive to keep paging across successive scroll-ups.
            await expect(async () => {
                await courseCommunication.scrollDiscussionToTop();
                expect(await courseCommunication.getDiscussionPost(oldestPost.id!).count()).toBe(1);
            }).toPass({ timeout: 30000, intervals: [700, 1000, 1000] });

            await courseCommunication.checkDiscussionPost(oldestPost.id!, 'Oldest discussion infinite scroll message');
        });
    });

    test.describe('Infinite scroll in course-wide search', () => {
        let oldestPost: Post;
        // A unique token placed in every seeded message so the search returns exactly this set of three pages.
        const searchToken = 'infscroll' + generateUUID().slice(0, 8);
        const messagesToSeed = 120;

        test.beforeEach('Create a channel with three pages of searchable messages', async ({ login, communicationAPIRequests }) => {
            await login(admin);
            const channelName = 'cws-' + generateUUID().slice(0, 8);
            const channel = await communicationAPIRequests.createCourseMessageChannel({ id: writeCourse.id } as any, channelName, 'Course-wide search channel', false, true);
            await communicationAPIRequests.joinUserIntoChannel({ id: writeCourse.id } as any, channel.id!, studentOne);

            // Seed as the student who will search, so the messages are part of their conversations and already read.
            await login(studentOne);
            oldestPost = await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, channel.id!, 'channel', `${searchToken} oldest message`);
            const fillerCount = messagesToSeed - 2;
            for (let batchStart = 0; batchStart < fillerCount; batchStart += 20) {
                await Promise.all(
                    Array.from({ length: Math.min(20, fillerCount - batchStart) }, (_, index) =>
                        communicationAPIRequests.createCourseMessage(
                            { id: writeCourse.id } as any,
                            channel.id!,
                            'channel',
                            `${searchToken} filler message ${batchStart + index + 1}`,
                        ),
                    ),
                );
            }
            await communicationAPIRequests.createCourseMessage({ id: writeCourse.id } as any, channel.id!, 'channel', `${searchToken} newest message`);
            await communicationAPIRequests.markConversationAsRead(writeCourse.id, channel.id!);
        });

        test('Scrolling up repeatedly chain-loads earlier pages of course-wide search results', async ({ login, courseMessages }) => {
            await login(studentOne, `/courses/${writeCourse.id}/communication`);
            await courseMessages.acceptCodeOfConductButton();

            // Search the whole course for the unique token; the first page of matching results renders.
            await courseMessages.searchCourseWide(searchToken);
            await expect.poll(() => courseMessages.getRenderedSearchResultCount(), { timeout: 30000 }).toBeGreaterThan(0);

            // The oldest match sits on the last result page. Scroll to the top repeatedly to chain-load earlier
            // pages until it is rendered. Before this fix the results container was not the scroll container and
            // was recreated on every fetch, so course-wide search never loaded a second page.
            await expect(async () => {
                await courseMessages.scrollMessagesToTop();
                expect(await courseMessages.getSinglePost(oldestPost.id!).count()).toBe(1);
            }).toPass({ timeout: 30000, intervals: [700, 1000, 1000] });

            await courseMessages.checkMessage(oldestPost.id!, `${searchToken} oldest message`);
        });
    });
});
