import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../helpers/conversationExampleModels';
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
} from 'app/shared/metis/conversations/conversation-permissions.utils';
import { Course } from 'app/entities/course.model';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';

describe('ConversationPermissionUtils', () => {
    describe('channels', () => {
        describe('canCreateNewMessageInConversation', () => {
            const channelsWhereNewMessageCanBeCreated = generateExampleChannelDTO({ isMember: true });

            it('can create new message in channel where user is member', () => {
                expect(canCreateNewMessageInConversation(channelsWhereNewMessageCanBeCreated)).toBeTrue();
            });

            it('can not create new message in channel where user is not member', () => {
                expect(canCreateNewMessageInConversation(generateExampleChannelDTO({ isMember: false }))).toBeFalse();
            });

            it('can not create new message in an archived channel', () => {
                expect(canCreateNewMessageInConversation(generateExampleChannelDTO({ isArchived: true }))).toBeFalse();
            });

            it('can not create new message in a an announcement channel where user is not moderator', () => {
                expect(canCreateNewMessageInConversation(generateExampleChannelDTO({ isMember: false, isAnnouncementChannel: true }))).toBeFalse();
            });

            it('can create new message in a an announcement channel where user is moderator', () => {
                expect(canCreateNewMessageInConversation(generateExampleChannelDTO({ isMember: true, isAnnouncementChannel: true, isChannelModerator: true }))).toBeTrue();
            });

            it('can create a new message in an announcement channel where user is not moderator but has moderation rights', () => {
                expect(
                    canCreateNewMessageInConversation(
                        generateExampleChannelDTO({ isMember: true, isAnnouncementChannel: true, isChannelModerator: false, hasChannelModerationRights: true }),
                    ),
                ).toBeTrue();
            });
        });

        describe('canLeaveConversation', () => {
            const channelsThatCanBeLeft = generateExampleChannelDTO({ isMember: true, isCreator: false });
            it('can leave channel', () => {
                expect(canLeaveConversation(channelsThatCanBeLeft)).toBeTrue();
            });

            it('creator cannot leave a channel', () => {
                expect(canLeaveConversation({ ...channelsThatCanBeLeft, isCreator: true })).toBeFalse();
            });

            it('non member cannot leave a channel', () => {
                expect(canLeaveConversation({ ...channelsThatCanBeLeft, isMember: false })).toBeFalse();
            });

            it('member cannot leave a course-wide channel', () => {
                expect(canLeaveConversation({ ...channelsThatCanBeLeft, isCourseWide: true } as ChannelDTO)).toBeFalse();
            });
        });

        describe('addUsersToConversation', () => {
            const channelWhereUsersCanBeAdded = generateExampleChannelDTO({ hasChannelModerationRights: true, isArchived: false });

            it('can add users to channel', () => {
                expect(canAddUsersToConversation(channelWhereUsersCanBeAdded)).toBeTrue();
            });

            it('should return false if the user is not a channel moderator and channel is public', () => {
                expect(canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, hasChannelModerationRights: false, isChannelModerator: false } as ChannelDTO)).toBeFalse();
            });

            it('should return false if the user is not a channel moderator and channel is private', () => {
                expect(
                    canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, hasChannelModerationRights: false, isPublic: false, isChannelModerator: false } as ChannelDTO),
                ).toBeFalse();
            });

            it('should return true if the channel is archived', () => {
                expect(canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, isPublic: true, isArchived: true } as ChannelDTO)).toBeTrue();
            });

            it('should return false if the channel is course wide', () => {
                expect(canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, isCourseWide: true } as ChannelDTO)).toBeFalse();
            });
        });

        describe('removeUsersFromConversation', () => {
            const channelsWhereUsersCanBeRemoved = generateExampleChannelDTO({ hasChannelModerationRights: true, isArchived: false, isPublic: false });

            it('can remove users to channel', () => {
                expect(canRemoveUsersFromConversation(channelsWhereUsersCanBeRemoved)).toBeTrue();
            });

            it('should return false if the user is not a channel moderator', () => {
                expect(canRemoveUsersFromConversation({ ...channelsWhereUsersCanBeRemoved, hasChannelModerationRights: false } as ChannelDTO)).toBeFalse();
            });

            it('should return true if the channel is archived', () => {
                expect(canRemoveUsersFromConversation({ ...channelsWhereUsersCanBeRemoved, isArchived: true } as ChannelDTO)).toBeTrue();
            });

            it('should return true if the channel is public', () => {
                expect(canRemoveUsersFromConversation({ ...channelsWhereUsersCanBeRemoved, isPublic: true } as ChannelDTO)).toBeTrue();
            });
        });

        describe('canCreateChannel', () => {
            const courseWithCorrectRights = { isAtLeastTutor: true } as Course;

            it('can create channel as tutor', () => {
                expect(canCreateChannel(courseWithCorrectRights)).toBeTrue();
            });

            it('can not create channel as student', () => {
                expect(canCreateChannel({ isAtLeastInstructor: false, isAtLeastTutor: false, isAtLeastEditor: false } as Course)).toBeFalse();
            });
        });
        describe('canDeleteChannel', () => {
            const courseWithCorrectRights = { isAtLeastInstructor: true } as Course;
            const channelWhereNoModerator = generateExampleChannelDTO({ hasChannelModerationRights: false, isChannelModerator: false, isCreator: false });

            it('can delete any channel as instructor', () => {
                expect(canDeleteChannel(courseWithCorrectRights, channelWhereNoModerator)).toBeTrue();
            });

            it('can not delete any channel as tutor', () => {
                expect(canDeleteChannel({ isAtLeastInstructor: false, isAtLeastTutor: true } as Course, channelWhereNoModerator)).toBeFalse();
            });

            const channelWhereModerator = generateExampleChannelDTO({ hasChannelModerationRights: true, isChannelModerator: true, isCreator: true });
            it('can delete self created channel as tutor', () => {
                expect(canDeleteChannel({ isAtLeastInstructor: false, isAtLeastTutor: true } as Course, channelWhereModerator)).toBeTrue();
            });

            const tutorialGroupChannel = generateExampleChannelDTO({
                hasChannelModerationRights: false,
                isChannelModerator: false,
                isCreator: false,
                tutorialGroupId: 1,
                tutorialGroupTitle: 'test',
            });
            it('can not delete tutorial group channel', () => {
                expect(canDeleteChannel(courseWithCorrectRights, tutorialGroupChannel)).toBeFalse();
            });
        });

        describe('can grant channel moderator role', () => {
            const channelWhereRoleCanBeGranted = generateExampleChannelDTO({ hasChannelModerationRights: true });

            it('can grant moderator role', () => {
                expect(canGrantChannelModeratorRole(channelWhereRoleCanBeGranted)).toBeTrue();
            });

            it('cannot grant moderator role without moderation rights', () => {
                expect(canGrantChannelModeratorRole({ ...channelWhereRoleCanBeGranted, hasChannelModerationRights: false })).toBeFalse();
            });
        });

        describe('can revoke channel moderator role', () => {
            const channelWhereRoleCanBeRevoked = generateExampleChannelDTO({ hasChannelModerationRights: true });

            it('can revoke moderator role', () => {
                expect(canRevokeChannelModeratorRole(channelWhereRoleCanBeRevoked)).toBeTrue();
            });

            it('cannot revoke moderator role without moderation rights', () => {
                expect(canRevokeChannelModeratorRole({ ...channelWhereRoleCanBeRevoked, hasChannelModerationRights: false })).toBeFalse();
            });
        });

        describe('can change channel archival state', () => {
            const channelThatCanBeArchived = generateExampleChannelDTO({ hasChannelModerationRights: true });

            it('can archive channel', () => {
                expect(canChangeChannelArchivalState(channelThatCanBeArchived)).toBeTrue();
            });

            it('cannot archive channel without moderation rights', () => {
                expect(canChangeChannelArchivalState({ ...channelThatCanBeArchived, hasChannelModerationRights: false })).toBeFalse();
            });
        });

        describe('can change channel properties', () => {
            const channelThatCanBeChanged = generateExampleChannelDTO({ hasChannelModerationRights: true, isArchived: false });

            it('can change channel properties', () => {
                expect(canChangeChannelProperties(channelThatCanBeChanged)).toBeTrue();
            });

            it('cannot change channel properties without moderation rights', () => {
                expect(canChangeChannelProperties({ ...channelThatCanBeChanged, hasChannelModerationRights: false })).toBeFalse();
            });

            it('can change channel properties of channel that is already archived', () => {
                expect(canChangeChannelProperties({ ...channelThatCanBeChanged, isArchived: true })).toBeTrue();
            });
        });

        describe('canJoinChannel', () => {
            const channelThatCanBeJoined = generateExampleChannelDTO({
                isMember: false,
                isPublic: true,
                isArchived: false,
                hasChannelModerationRights: false,
                isChannelModerator: false,
            });

            it('can join channel', () => {
                expect(canJoinChannel(channelThatCanBeJoined)).toBeTrue();
            });

            it('can not join a channel twice', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isMember: true })).toBeFalse();
            });

            it('can not join a private channel without moderation rights', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isPublic: false })).toBeFalse();
            });

            it('can join an archived public channel without moderation rights', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isArchived: true })).toBeTrue();
            });

            it('can join a private channel with moderation rights', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isPublic: false, hasChannelModerationRights: true })).toBeTrue();
            });
        });
    });

    describe('one-on-one chats', () => {
        describe('canCreateNewMessageInConversation', () => {
            const chatThatCanBePostedIn = generateOneToOneChatDTO({ isMember: true });

            it('can create new message in channel where user is member', () => {
                expect(canCreateNewMessageInConversation(chatThatCanBePostedIn)).toBeTrue();
            });

            it('can not create new message in channel where user is not member', () => {
                expect(canCreateNewMessageInConversation(generateOneToOneChatDTO({ isMember: false }))).toBeFalse();
            });
        });

        describe('canLeaveConversation', () => {
            it('is not possible to leave a one to one chat', () => {
                expect(canLeaveConversation(generateOneToOneChatDTO({}))).toBeFalse();
            });
        });

        describe('addUsersToConversation', () => {
            it('is not possible to add users to a one-on-one chat', () => {
                expect(canAddUsersToConversation(generateOneToOneChatDTO({}))).toBeFalse();
            });
        });

        describe('removeUsersFromConversation', () => {
            it('is not possible to remove users to a one-on-one chat', () => {
                expect(canRemoveUsersFromConversation(generateOneToOneChatDTO({}))).toBeFalse();
            });
        });
    });

    describe('groupChats', () => {
        describe('canCreateNewMessageInConversation', () => {
            const chatThatCanBePostedIn = generateOneToOneChatDTO({ isMember: true });

            it('can create new message in channel where user is member', () => {
                expect(canCreateNewMessageInConversation(chatThatCanBePostedIn)).toBeTrue();
            });

            it('can not create new message in channel where user is not member', () => {
                expect(canCreateNewMessageInConversation(generateOneToOneChatDTO({ isMember: false }))).toBeFalse();
            });
        });

        describe('canLeaveConversation', () => {
            const groupChatThatCanBeLeft = generateExampleGroupChatDTO({ isMember: true });
            it('can leave channel', () => {
                expect(canLeaveConversation(groupChatThatCanBeLeft)).toBeTrue();
            });

            it('non member can not leave a group chat', () => {
                expect(canLeaveConversation({ ...groupChatThatCanBeLeft, isMember: false })).toBeFalse();
            });
        });
        describe('addUsersToConversation', () => {
            const groupChatWhereUsersCanBeAdded = generateExampleGroupChatDTO({ isMember: true });

            it('can add users to channel', () => {
                expect(canAddUsersToConversation(groupChatWhereUsersCanBeAdded)).toBeTrue();
            });

            it('should return false if the user is not a member of the group chat', () => {
                expect(canAddUsersToConversation({ ...groupChatWhereUsersCanBeAdded, isMember: false })).toBeFalse();
            });
        });

        describe('removeUsersFromConversation', () => {
            const groupChatWhereUsersCanBeRemoved = generateExampleGroupChatDTO({ isMember: true });

            it('can remove users to channel', () => {
                expect(canRemoveUsersFromConversation(groupChatWhereUsersCanBeRemoved)).toBeTrue();
            });

            it('should return false if the user is not a member of the group chat', () => {
                expect(canRemoveUsersFromConversation({ ...groupChatWhereUsersCanBeRemoved, isMember: false })).toBeFalse();
            });
        });

        describe('can change group chat properties', () => {
            const groupChatThatCanBeChanged = generateExampleGroupChatDTO({ isMember: true });

            it('can change group chat properties', () => {
                expect(canChangeGroupChatProperties(groupChatThatCanBeChanged)).toBeTrue();
            });

            it('cannot change group chat properties without being a member', () => {
                expect(canChangeGroupChatProperties({ ...groupChatThatCanBeChanged, isMember: false })).toBeFalse();
            });
        });
    });
});
