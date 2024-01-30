import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { ChannelDTO, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { GroupChatDTO, isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { Course } from 'app/entities/course.model';
import { isOneToOneChatDto } from 'app/entities/metis/conversation/one-to-one-chat.model';

export function canAddUsersToConversation(conversation: ConversationDTO): boolean {
    if (!conversation) {
        return false;
    }
    const groupChatCheck = (groupChat: GroupChatDTO): boolean => !!groupChat.isMember;
    const channelCheck = (channel: ChannelDTO): boolean => !channel.isCourseWide && hasChannelModerationRights(channel);

    if (isChannelDto(conversation)) {
        return channelCheck(conversation);
    } else if (isGroupChatDTO(conversation)) {
        return groupChatCheck(conversation);
    } else if (isOneToOneChatDto(conversation)) {
        return false;
    } else {
        throw new Error('Conversation type not supported');
    }
}

export function canCreateNewMessageInConversation(conversation: ConversationDTO): boolean {
    if (!conversation) {
        return false;
    }
    const groupChatCheck = (groupChat: GroupChatDTO): boolean => !!groupChat.isMember;
    const oneToOneChatCheck = (oneToOneChat: ConversationDTO): boolean => {
        return !!oneToOneChat.isMember;
    };
    const channelCheck = (channel: ChannelDTO): boolean =>
        !!channel.isMember && !channel.isArchived && (!channel.isAnnouncementChannel || (channel.isAnnouncementChannel && !!channel.hasChannelModerationRights));

    if (isChannelDto(conversation)) {
        return channelCheck(conversation);
    } else if (isGroupChatDTO(conversation)) {
        return groupChatCheck(conversation);
    } else if (isOneToOneChatDto(conversation)) {
        return oneToOneChatCheck(conversation);
    } else {
        throw new Error('Conversation type not supported');
    }
}

export function canGrantChannelModeratorRole(channel: ChannelDTO): boolean {
    return hasChannelModerationRights(channel);
}

export function canRevokeChannelModeratorRole(channel: ChannelDTO): boolean {
    return hasChannelModerationRights(channel);
}

export function canRemoveUsersFromConversation(conversation: ConversationDTO): boolean {
    if (!conversation) {
        return false;
    }
    const groupChatCheck = (groupChat: GroupChatDTO): boolean => !!groupChat.isMember;
    const channelCheck = (channel: ChannelDTO): boolean => !!channel.hasChannelModerationRights;

    if (isChannelDto(conversation)) {
        return channelCheck(conversation);
    } else if (isGroupChatDTO(conversation)) {
        return groupChatCheck(conversation);
    } else if (isOneToOneChatDto(conversation)) {
        return false;
    } else {
        throw new Error('Conversation type not supported');
    }
}

export function canLeaveConversation(conversation: ConversationDTO): boolean {
    if (!conversation) {
        return false;
    }
    // not possible to leave a conversation as not a member
    if (!conversation.isMember) {
        return false;
    }
    // the creator of a channel can not leave it
    // if the channel is course-wide, you also cannot leave it
    if (isChannelDto(conversation) && (conversation?.isCreator || conversation?.isCourseWide)) {
        return false;
    }
    if (isOneToOneChatDto(conversation)) {
        return false;
    }
    return true;
}

export function canJoinChannel(channel: ChannelDTO): boolean {
    if (!channel) {
        return false;
    }
    // already joined channel
    if (channel.isMember) {
        return false;
    }
    // private channels only be self-joined by instructors which are the only non members who have channel moderation rights
    if (hasChannelModerationRights(channel)) {
        return true;
    }
    return !!channel.isPublic;
}

export function canChangeChannelArchivalState(channel: ChannelDTO): boolean {
    return hasChannelModerationRights(channel);
}

export function canDeleteChannel(course: Course, channelDTO: ChannelDTO): boolean {
    const isCreator = channelDTO.isCreator;
    const isInstructor = course.isAtLeastInstructor;
    const isChannelModerator = channelDTO.isChannelModerator;
    // tutorial group channels can not be deleted by a user
    const isTutorialGroupChannel = !!channelDTO.tutorialGroupId || !!channelDTO.tutorialGroupTitle;

    return !isTutorialGroupChannel && (!!isInstructor || !!(isChannelModerator && isCreator));
}

export function canCreateChannel(course: Course): boolean {
    return course?.isAtLeastTutor ?? false;
}

export function canChangeChannelProperties(channel: ChannelDTO): boolean {
    if (!channel) {
        return false;
    }
    return !!channel.hasChannelModerationRights;
}

export function canChangeGroupChatProperties(groupChat: GroupChatDTO): boolean {
    if (!groupChat) {
        return false;
    }
    return !!groupChat.isMember;
}

const hasChannelModerationRights = (channel: ChannelDTO) => {
    if (!channel) {
        return false;
    }
    return !!channel.hasChannelModerationRights;
};
