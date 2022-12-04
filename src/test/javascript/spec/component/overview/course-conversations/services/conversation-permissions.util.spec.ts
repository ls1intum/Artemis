import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../helpers/conversationExampleModels';
import {
    canAddUsersToConversation,
    canChangeChannelArchivalState,
    canChangeChannelProperties,
    canChangeGroupChatProperties,
    canCreateChannel,
    canDeleteChannel,
    canGrantChannelAdminRights,
    canJoinChannel,
    canLeaveConversation,
    canRemoveUsersFromConversation,
    canRevokeChannelAdminRights,
} from 'app/shared/metis/conversations/conversation-permissions.utils';
import { Course } from 'app/entities/course.model';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';

describe('ConversationPermissionUtils', () => {
    describe('channels', () => {
        describe('canLeaveConversation', () => {
            const channelsThatCanBeLeft = generateExampleChannelDTO({ isMember: true, isCreator: false });
            it('can leave channel', () => {
                expect(canLeaveConversation(channelsThatCanBeLeft)).toBeTrue();
            });

            it('creator can not leave a channel', () => {
                expect(canLeaveConversation({ ...channelsThatCanBeLeft, isCreator: true })).toBeFalse();
            });

            it('non member can not leave a channel', () => {
                expect(canLeaveConversation({ ...channelsThatCanBeLeft, isMember: false })).toBeFalse();
            });
        });

        describe('addUsersToConversation', () => {
            const channelWhereUsersCanBeAdded = generateExampleChannelDTO({ hasChannelAdminRights: true, isArchived: false });

            it('can add users to channel', () => {
                expect(canAddUsersToConversation(channelWhereUsersCanBeAdded)).toBeTrue();
            });

            it('should return false if the user is not a channel admin and channel is public', () => {
                expect(canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, hasChannelAdminRights: false, isAdmin: false } as ChannelDTO)).toBeFalse();
            });

            it('should return false if the user is not a channel admin and channel is private', () => {
                expect(canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, hasChannelAdminRights: false, isPublic: false, isAdmin: false } as ChannelDTO)).toBeFalse();
            });

            it('should return true if the channel is archived', () => {
                expect(canAddUsersToConversation({ ...channelWhereUsersCanBeAdded, isPublic: true, isArchived: true } as ChannelDTO)).toBeTrue();
            });
        });

        describe('removeUsersFromConversation', () => {
            const channelsWhereUsersCanBeRemoved = generateExampleChannelDTO({ hasChannelAdminRights: true, isArchived: false, isPublic: false });

            it('can remove users to channel', () => {
                expect(canRemoveUsersFromConversation(channelsWhereUsersCanBeRemoved)).toBeTrue();
            });

            it('should return false if the user is not a channel admin', () => {
                expect(canRemoveUsersFromConversation({ ...channelsWhereUsersCanBeRemoved, hasChannelAdminRights: false } as ChannelDTO)).toBeFalse();
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

            it('can delete channel as instructor', () => {
                expect(canDeleteChannel(courseWithCorrectRights)).toBeTrue();
            });

            it('can not delete channel as tutor', () => {
                expect(canDeleteChannel({ isAtLeastInstructor: false, isAtLeastTutor: true } as Course)).toBeFalse();
            });
        });

        describe('can grant channel admin rights', () => {
            const channelWhereRightsCanBeGranted = generateExampleChannelDTO({ hasChannelAdminRights: true });

            it('can grant admin rights', () => {
                expect(canGrantChannelAdminRights(channelWhereRightsCanBeGranted)).toBeTrue();
            });

            it('cannot grant admin rights without admin rights', () => {
                expect(canGrantChannelAdminRights({ ...channelWhereRightsCanBeGranted, hasChannelAdminRights: false })).toBeFalse();
            });
        });

        describe('can revoke channel admin rights', () => {
            const channelWhereRightsCanBeRevoked = generateExampleChannelDTO({ hasChannelAdminRights: true });

            it('can revoke admin rights', () => {
                expect(canRevokeChannelAdminRights(channelWhereRightsCanBeRevoked)).toBeTrue();
            });

            it('cannot revoke admin rights without admin rights', () => {
                expect(canRevokeChannelAdminRights({ ...channelWhereRightsCanBeRevoked, hasChannelAdminRights: false })).toBeFalse();
            });
        });

        describe('can change channel archival state', () => {
            const channelThatCanBeArchived = generateExampleChannelDTO({ hasChannelAdminRights: true });

            it('can archive channel', () => {
                expect(canChangeChannelArchivalState(channelThatCanBeArchived)).toBeTrue();
            });

            it('cannot archive channel without admin rights', () => {
                expect(canChangeChannelArchivalState({ ...channelThatCanBeArchived, hasChannelAdminRights: false })).toBeFalse();
            });
        });

        describe('can change channel properties', () => {
            const channelThatCanBeChanged = generateExampleChannelDTO({ hasChannelAdminRights: true, isArchived: false });

            it('can change channel properties', () => {
                expect(canChangeChannelProperties(channelThatCanBeChanged)).toBeTrue();
            });

            it('cannot change channel properties without admin rights', () => {
                expect(canChangeChannelProperties({ ...channelThatCanBeChanged, hasChannelAdminRights: false })).toBeFalse();
            });

            it('can change channel properties of channel that is already archived', () => {
                expect(canChangeChannelProperties({ ...channelThatCanBeChanged, isArchived: true })).toBeTrue();
            });
        });

        describe('canJoinChannel', () => {
            const channelThatCanBeJoined = generateExampleChannelDTO({ isMember: false, isPublic: true, isArchived: false, hasChannelAdminRights: false, isAdmin: false });

            it('can join channel', () => {
                expect(canJoinChannel(channelThatCanBeJoined)).toBeTrue();
            });

            it('can not join a channel twice', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isMember: true })).toBeFalse();
            });

            it('can not join a private channel without admin rights', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isPublic: false })).toBeFalse();
            });

            it('can join an archived public channel without admin rights', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isArchived: true })).toBeTrue();
            });

            it('can join a private channel with admin rights', () => {
                expect(canJoinChannel({ ...channelThatCanBeJoined, isPublic: false, hasChannelAdminRights: true })).toBeTrue();
            });
        });
    });

    describe('one-on-one chats', () => {
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
