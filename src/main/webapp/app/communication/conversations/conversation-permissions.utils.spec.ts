import { describe, expect, it } from 'vitest';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import {
    canAddUsersToConversation,
    canChangeChannelArchivalState,
    canChangeChannelProperties,
    canChangeGroupChatProperties,
    canCreateChannel,
    canCreateNewMessageInConversation,
    canDeleteChannel,
    canGrantChannelModeratorRole,
    canJoinChannel,
    canLeaveConversation,
    canRemoveUsersFromConversation,
    canRevokeChannelModeratorRole,
} from 'app/communication/conversations/conversation-permissions.utils';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';

describe('ConversationPermissionUtils', () => {
    describe('channels', () => {
        describe('canCreateNewMessageInConversation', () => {
            const channelsWhereNewMessageCanBeCreated = generateExampleChannelDTO({ isMember: true } as ChannelDTO);

            it('can create new message in channel where user is member', () => {
                expect(canCreateNewMessageInConversation(channelsWhereNewMessageCanBeCreated)).toBe(true);
            });

            it('can not create new message in channel where user is not member', () => {
                expect(canCreateNewMessageInConversation(generateExampleChannelDTO({ isMember: false } as ChannelDTO))).toBe(false);
            });

            it('can not create new message in an archived channel', () => {
                expect(canCreateNewMessageInConversation(generateExampleChannelDTO({ isArchived: true } as ChannelDTO))).toBe(false);
            });

            it('can not create new message in a an announcement channel where user is not moderator', () => {
                expect(canCreateNewMessageInConversation(generateExampleChannelDTO({ isMember: false, isAnnouncementChannel: true } as ChannelDTO))).toBe(false);
            });

            it('can create new message in a an announcement channel where user is moderator', () => {
                expect(canCreateNewMessageInConversation(generateExampleChannelDTO({ isMember: true, isAnnouncementChannel: true, isChannelModerator: true } as ChannelDTO))).toBe(
                    true,
                );
            });

            it('can create a new message in an announcement channel where user is not moderator but has moderation rights', () => {
                expect(
                    canCreateNewMessageInConversation(
                        generateExampleChannelDTO({ isMember: true, isAnnouncementChannel: true, isChannelModerator: false, hasChannelModerationRights: true } as ChannelDTO),
                    ),
                ).toBe(true);
            });
        });

        describe('canLeaveConversation', () => {
            const channelsThatCanBeLeft = generateExampleChannelDTO({ isMember: true, isCreator: false } as ChannelDTO);
            it('can leave channel', () => {
                expect(canLeaveConversation(channelsThatCanBeLeft)).toBe(true);
            });

            it('creator cannot leave a channel', () => {
                expect(canLeaveConversation({ ...channelsThatCanBeLeft, isCreator: true })).toBe(false);
            });

            it('non member cannot leave a channel', () => {
                expect(canLeaveConversation({ ...channelsThatCanBeLeft, isMember: false })).toBe(false);
            });

            it('member cannot leave a course-wide channel', () => {
                expect(canLeaveConversation({ ...channelsThatCanBeLeft, isCourseWide: true } as ChannelDTO)).toBe(false);
            });
        });

        describe('addUsersToConversation', () => {
            const channelWhereUsersCanBeAdded = generateExampleChannelDTO({ hasChannelModerationRights: true, isArchived: false } as ChannelDTO);

            it('can add users to channel', () => {
                expect(canAddUsersToConversation(channelWhereUsersCanBeAdded)).toBe(true);
            });

            it('should return false if the user is not a channel moderator and channel is public', () => {
                expect(canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, hasChannelModerationRights: false, isChannelModerator: false } as ChannelDTO)).toBe(false);
            });

            it('should return false if the user is not a channel moderator and channel is private', () => {
                expect(
                    canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, hasChannelModerationRights: false, isPublic: false, isChannelModerator: false } as ChannelDTO),
                ).toBe(false);
            });

            it('should return true if the channel is archived', () => {
                expect(canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, isPublic: true, isArchived: true } as ChannelDTO)).toBe(true);
            });

            it('should return false if the channel is course wide', () => {
                expect(canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, isCourseWide: true } as ChannelDTO)).toBe(false);
            });
        });

        describe('removeUsersFromConversation', () => {
            const channelsWhereUsersCanBeRemoved = generateExampleChannelDTO({ hasChannelModerationRights: true, isArchived: false, isPublic: false } as ChannelDTO);

            it('can remove users to channel', () => {
                expect(canRemoveUsersFromConversation(channelsWhereUsersCanBeRemoved)).toBe(true);
            });

            it('should return false if the user is not a channel moderator', () => {
                expect(canRemoveUsersFromConversation({ ...channelsWhereUsersCanBeRemoved, hasChannelModerationRights: false } as ChannelDTO)).toBe(false);
            });

            it('should return true if the channel is archived', () => {
                expect(canRemoveUsersFromConversation({ ...channelsWhereUsersCanBeRemoved, isArchived: true } as ChannelDTO)).toBe(true);
            });

            it('should return true if the channel is public', () => {
                expect(canRemoveUsersFromConversation({ ...channelsWhereUsersCanBeRemoved, isPublic: true } as ChannelDTO)).toBe(true);
            });
        });

        describe('canCreateChannel', () => {
            const courseWithCorrectRights = { isAtLeastTutor: true } as Course;

            it('can create channel as tutor', () => {
                expect(canCreateChannel(courseWithCorrectRights)).toBe(true);
            });

            it('can not create channel as student', () => {
                expect(canCreateChannel({ isAtLeastInstructor: false, isAtLeastTutor: false, isAtLeastEditor: false } as Course)).toBe(false);
            });
        });
        describe('canDeleteChannel', () => {
            const courseWithCorrectRights = { isAtLeastInstructor: true } as Course;
            const channelWhereNoModerator = generateExampleChannelDTO({ hasChannelModerationRights: false, isChannelModerator: false, isCreator: false } as ChannelDTO);

            it('can delete any channel as instructor', () => {
                expect(canDeleteChannel(courseWithCorrectRights, channelWhereNoModerator)).toBe(true);
            });

            it('can not delete any channel as tutor', () => {
                expect(canDeleteChannel({ isAtLeastInstructor: false, isAtLeastTutor: true } as Course, channelWhereNoModerator)).toBe(false);
            });

            const channelWhereModerator = generateExampleChannelDTO({ hasChannelModerationRights: true, isChannelModerator: true, isCreator: true } as ChannelDTO);
            it('can delete self created channel as tutor', () => {
                expect(canDeleteChannel({ isAtLeastInstructor: false, isAtLeastTutor: true } as Course, channelWhereModerator)).toBe(true);
            });

            const tutorialGroupChannel = generateExampleChannelDTO({
                hasChannelModerationRights: false,
                isChannelModerator: false,
                isCreator: false,
                tutorialGroupId: 1,
                tutorialGroupTitle: 'test',
            } as ChannelDTO);
            it('can not delete tutorial group channel', () => {
                expect(canDeleteChannel(courseWithCorrectRights, tutorialGroupChannel)).toBe(false);
            });
        });

        describe('can grant channel moderator role', () => {
            const channelWhereRoleCanBeGranted = generateExampleChannelDTO({ hasChannelModerationRights: true } as ChannelDTO);

            it('can grant moderator role', () => {
                expect(canGrantChannelModeratorRole(channelWhereRoleCanBeGranted)).toBe(true);
            });

            it('cannot grant moderator role without moderation rights', () => {
                expect(canGrantChannelModeratorRole({ ...channelWhereRoleCanBeGranted, hasChannelModerationRights: false })).toBe(false);
            });
        });

        describe('can revoke channel moderator role', () => {
            const channelWhereRoleCanBeRevoked = generateExampleChannelDTO({ hasChannelModerationRights: true } as ChannelDTO);

            it('can revoke moderator role', () => {
                expect(canRevokeChannelModeratorRole(channelWhereRoleCanBeRevoked)).toBe(true);
            });

            it('cannot revoke moderator role without moderation rights', () => {
                expect(canRevokeChannelModeratorRole({ ...channelWhereRoleCanBeRevoked, hasChannelModerationRights: false })).toBe(false);
            });
        });

        describe('can change channel archival state', () => {
            const channelThatCanBeArchived = generateExampleChannelDTO({ hasChannelModerationRights: true } as ChannelDTO);

            it('can archive channel', () => {
                expect(canChangeChannelArchivalState(channelThatCanBeArchived)).toBe(true);
            });

            it('cannot archive channel without moderation rights', () => {
                expect(canChangeChannelArchivalState({ ...channelThatCanBeArchived, hasChannelModerationRights: false })).toBe(false);
            });
        });

        describe('can change channel properties', () => {
            const channelThatCanBeChanged = generateExampleChannelDTO({ hasChannelModerationRights: true, isArchived: false } as ChannelDTO);

            it('can change channel properties', () => {
                expect(canChangeChannelProperties(channelThatCanBeChanged)).toBe(true);
            });

            it('cannot change channel properties without moderation rights', () => {
                expect(canChangeChannelProperties({ ...channelThatCanBeChanged, hasChannelModerationRights: false })).toBe(false);
            });

            it('can change channel properties of channel that is already archived', () => {
                expect(canChangeChannelProperties({ ...channelThatCanBeChanged, isArchived: true })).toBe(true);
            });
        });

        describe('canJoinChannel', () => {
            const channelThatCanBeJoined = generateExampleChannelDTO({
                isMember: false,
                isPublic: true,
                isArchived: false,
                hasChannelModerationRights: false,
                isChannelModerator: false,
            } as ChannelDTO);

            it('can join channel', () => {
                expect(canJoinChannel(channelThatCanBeJoined)).toBe(true);
            });

            it('can not join a channel twice', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isMember: true })).toBe(false);
            });

            it('can not join a private channel without moderation rights', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isPublic: false })).toBe(false);
            });

            it('can join an archived public channel without moderation rights', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isArchived: true })).toBe(true);
            });

            it('can join a private channel with moderation rights', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isPublic: false, hasChannelModerationRights: true })).toBe(true);
            });
        });
    });

    describe('one-on-one chats', () => {
        describe('canCreateNewMessageInConversation', () => {
            const chatThatCanBePostedIn = generateOneToOneChatDTO({ isMember: true });

            it('can create new message in channel where user is member', () => {
                expect(canCreateNewMessageInConversation(chatThatCanBePostedIn)).toBe(true);
            });

            it('can not create new message in channel where user is not member', () => {
                expect(canCreateNewMessageInConversation(generateOneToOneChatDTO({ isMember: false }))).toBe(false);
            });
        });

        describe('canLeaveConversation', () => {
            it('is not possible to leave a one to one chat', () => {
                expect(canLeaveConversation(generateOneToOneChatDTO({}))).toBe(false);
            });
        });

        describe('addUsersToConversation', () => {
            it('is not possible to add users to a one-on-one chat', () => {
                expect(canAddUsersToConversation(generateOneToOneChatDTO({}))).toBe(false);
            });
        });

        describe('removeUsersFromConversation', () => {
            it('is not possible to remove users to a one-on-one chat', () => {
                expect(canRemoveUsersFromConversation(generateOneToOneChatDTO({}))).toBe(false);
            });
        });
    });

    describe('groupChats', () => {
        describe('canCreateNewMessageInConversation', () => {
            const chatThatCanBePostedIn = generateOneToOneChatDTO({ isMember: true });

            it('can create new message in channel where user is member', () => {
                expect(canCreateNewMessageInConversation(chatThatCanBePostedIn)).toBe(true);
            });

            it('can not create new message in channel where user is not member', () => {
                expect(canCreateNewMessageInConversation(generateOneToOneChatDTO({ isMember: false }))).toBe(false);
            });
        });

        describe('canLeaveConversation', () => {
            const groupChatThatCanBeLeft = generateExampleGroupChatDTO({ isMember: true });
            it('can leave channel', () => {
                expect(canLeaveConversation(groupChatThatCanBeLeft)).toBe(true);
            });

            it('non member can not leave a group chat', () => {
                expect(canLeaveConversation({ ...groupChatThatCanBeLeft, isMember: false })).toBe(false);
            });
        });
        describe('addUsersToConversation', () => {
            const groupChatWhereUsersCanBeAdded = generateExampleGroupChatDTO({ isMember: true });

            it('can add users to channel', () => {
                expect(canAddUsersToConversation(groupChatWhereUsersCanBeAdded)).toBe(true);
            });

            it('should return false if the user is not a member of the group chat', () => {
                expect(canAddUsersToConversation({ ...groupChatWhereUsersCanBeAdded, isMember: false })).toBe(false);
            });
        });

        describe('removeUsersFromConversation', () => {
            const groupChatWhereUsersCanBeRemoved = generateExampleGroupChatDTO({ isMember: true });

            it('can remove users to channel', () => {
                expect(canRemoveUsersFromConversation(groupChatWhereUsersCanBeRemoved)).toBe(true);
            });

            it('should return false if the user is not a member of the group chat', () => {
                expect(canRemoveUsersFromConversation({ ...groupChatWhereUsersCanBeRemoved, isMember: false })).toBe(false);
            });
        });

        describe('can change group chat properties', () => {
            const groupChatThatCanBeChanged = generateExampleGroupChatDTO({ isMember: true });

            it('can change group chat properties', () => {
                expect(canChangeGroupChatProperties(groupChatThatCanBeChanged)).toBe(true);
            });

            it('cannot change group chat properties without being a member', () => {
                expect(canChangeGroupChatProperties({ ...groupChatThatCanBeChanged, isMember: false })).toBe(false);
            });
        });
    });
});
