import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationDTO, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import type { Channel, ChannelSubTypeEnum } from 'app/openapi/model/channel';
import type { ConversationUser } from 'app/openapi/model/conversation-user';
import type { GroupChat } from 'app/openapi/model/group-chat';

const channelSubTypes: Record<ChannelSubTypeEnum, ChannelSubType> = {
    general: ChannelSubType.GENERAL,
    exercise: ChannelSubType.EXERCISE,
    lecture: ChannelSubType.LECTURE,
    exam: ChannelSubType.EXAM,
    feedbackDiscussion: ChannelSubType.FEEDBACK_DISCUSSION,
};

function toConversationUserDTO(user: ConversationUser): ConversationUserDTO {
    const dto = new ConversationUserDTO();
    dto.id = user.id;
    dto.login = user.login;
    dto.name = user.name;
    dto.firstName = user.firstName;
    dto.lastName = user.lastName;
    dto.imageUrl = user.imageUrl;
    dto.isInstructor = user.isInstructor;
    dto.isEditor = user.isEditor;
    dto.isTeachingAssistant = user.isTeachingAssistant;
    dto.isStudent = user.isStudent;
    dto.isChannelModerator = user.isChannelModerator;
    dto.isRequestingUser = user.isRequestingUser;
    return dto;
}

function mapConversationFields(source: Channel | GroupChat, dto: ConversationDTO): void {
    dto.id = source.id;
    dto.creationDate = convertDateStringFromServer(source.creationDate);
    dto.lastMessageDate = convertDateStringFromServer(source.lastMessageDate);
    dto.creator = source.creator && toConversationUserDTO(source.creator);
    dto.lastReadDate = convertDateStringFromServer(source.lastReadDate);
    dto.unreadMessagesCount = source.unreadMessagesCount;
    dto.isFavorite = source.isFavorite;
    dto.isHidden = source.isHidden;
    dto.isMuted = source.isMuted;
    dto.isCreator = source.isCreator;
    dto.isMember = source.isMember;
    dto.numberOfMembers = source.numberOfMembers;
}

/** Hydrates a channel response for consumers of the conversation classes. */
export function toChannelDTO(channel: Channel): ChannelDTO {
    const dto = new ChannelDTO();
    mapConversationFields(channel, dto);
    dto.name = channel.name;
    dto.description = channel.description;
    dto.topic = channel.topic;
    dto.isPublic = channel.isPublic ?? dto.isPublic;
    dto.isAnnouncementChannel = channel.isAnnouncementChannel ?? dto.isAnnouncementChannel;
    dto.isArchived = channel.isArchived ?? dto.isArchived;
    dto.isCourseWide = channel.isCourseWide ?? dto.isCourseWide;
    dto.hasChannelModerationRights = channel.hasChannelModerationRights ?? dto.hasChannelModerationRights;
    dto.isChannelModerator = channel.isChannelModerator ?? dto.isChannelModerator;
    dto.tutorialGroupId = channel.tutorialGroupId;
    dto.tutorialGroupTitle = channel.tutorialGroupTitle;
    dto.subType = channel.subType === undefined ? undefined : channelSubTypes[channel.subType];
    dto.subTypeReferenceId = channel.subTypeReferenceId;
    dto.subTypeReferenceStartDate = convertDateStringFromServer(channel.subTypeReferenceStartDate);
    dto.subTypeReferenceEndDate = convertDateStringFromServer(channel.subTypeReferenceEndDate);
    return dto;
}

/** Hydrates a group chat response and its members. */
export function toGroupChatDTO(groupChat: GroupChat): GroupChatDTO {
    const dto = new GroupChatDTO();
    mapConversationFields(groupChat, dto);
    dto.name = groupChat.name;
    dto.members = groupChat.members?.map(toConversationUserDTO);
    return dto;
}

/** Maps writable channel fields and the subtype required by Jackson. */
export function toChannelRequest(dto: ChannelDTO): Channel {
    return {
        type: ConversationType.CHANNEL,
        name: dto.name,
        description: dto.description,
        topic: dto.topic,
        isPublic: dto.isPublic,
        isAnnouncementChannel: dto.isAnnouncementChannel,
        isArchived: dto.isArchived,
        isCourseWide: dto.isCourseWide,
    };
}

/** Maps the group chat update payload. */
export function toGroupChatRequest(dto: GroupChatDTO): GroupChat {
    return { type: ConversationType.GROUP_CHAT, name: dto.name };
}
