import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { ChannelDTO, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { GroupChatDto, isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { Course } from 'app/entities/course.model';
import { isOneToOneChatDto } from 'app/entities/metis/conversation/one-to-one-chat.model';

export function canAddUsersToConversation(conversation: ConversationDto): boolean {
    if (!conversation) {
        return false;
    }
    const groupChatCheck = (groupChat: GroupChatDto): boolean => !!groupChat.isMember;
    const channelCheck = (channel: ChannelDTO): boolean => !!channel.hasChannelAdminRights && !channel?.isArchived;

    if (isChannelDto(conversation)) {
        return channelCheck(conversation);
    } else if (isGroupChatDto(conversation)) {
        return groupChatCheck(conversation);
    } else if (isOneToOneChatDto(conversation)) {
        return false;
    } else {
        throw new Error('Conversation type not supported');
    }
}

export function canGrantChannelAdminRights(channel: ChannelDTO): boolean {
    return hasChannelAdminRightsCheck(channel);
}

export function canRevokeChannelAdminRights(channel: ConversationDto): boolean {
    return hasChannelAdminRightsCheck(channel);
}

export function canRemoveUsersFromConversation(conversation: ConversationDto): boolean {
    if (!conversation) {
        return false;
    }
    const groupChatCheck = (groupChat: GroupChatDto): boolean => !!groupChat.isMember;
    const channelCheck = (channel: ChannelDTO): boolean => !!channel.hasChannelAdminRights && !channel?.isArchived && !channel?.isPublic;

    if (isChannelDto(conversation)) {
        return channelCheck(conversation);
    } else if (isGroupChatDto(conversation)) {
        return groupChatCheck(conversation);
    } else if (isOneToOneChatDto(conversation)) {
        return false;
    } else {
        throw new Error('Conversation type not supported');
    }
}

export function canLeaveConversation(conversation: ConversationDto): boolean {
    if (!conversation) {
        return false;
    }
    // not  possible to leave a conversation as not a member
    if (!conversation.isMember) {
        return false;
    }
    // the creator of a channel can not leave it
    if (isChannelDto(conversation) && conversation.isCreator) {
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

    // with admin rights you can always join a channel private or public
    if (channel.hasChannelAdminRights) {
        return true;
    } else {
        // without admin rights you can only join a channel if it is public and not archived
        return !!channel.isPublic && !channel.isArchived;
    }
}

export function canChangeChannelArchivalState(channel: ChannelDTO): boolean {
    return hasChannelAdminRightsCheck(channel);
}

export function canDeleteChannel(channel: ChannelDTO): boolean {
    return hasChannelAdminRightsCheck(channel);
}

export function canCreateChannel(course: Course): boolean {
    return course?.isAtLeastInstructor ?? false;
}

export function canChangeChannelProperties(channel: ChannelDTO): boolean {
    if (!channel) {
        return false;
    }
    return !channel.isArchived && !!channel.hasChannelAdminRights;
}

export function canChangeGroupChatProperties(groupChat: GroupChatDto): boolean {
    if (!groupChat) {
        return false;
    }
    return !!groupChat.isMember;
}

const hasChannelAdminRightsCheck = (channel: ChannelDTO) => {
    if (!channel) {
        return false;
    }
    return !!channel.hasChannelAdminRights;
};
