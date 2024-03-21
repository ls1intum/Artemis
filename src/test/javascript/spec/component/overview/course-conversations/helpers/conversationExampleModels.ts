import dayjs from 'dayjs/esm';
import { ChannelDTO, ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { GroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';

export const generateExampleChannelDTO = ({
    id = 1,
    name = 'general',
    description = 'general channel',
    topic = 'general',
    isPublic = true,
    isArchived = false,
    isChannelModerator = true,
    hasChannelModerationRights = true,
    creationDate = dayjs(),
    lastMessageDate = dayjs(),
    lastReadDate = dayjs(),
    numberOfMembers = 10,
    creator = { id: 1, login: 'login', firstName: 'Kaddl', lastName: 'Garching' } as ConversationUserDTO,
    isCreator = true,
    isFavorite = false,
    isHidden = false,
    isMuted = false,
    isMember = true,
    isAnnouncementChannel = false,
    isCourseWide = false,
    unreadMessagesCount = 0,
    tutorialGroupTitle = undefined,
    tutorialGroupId = undefined,
    subType = ChannelSubType.GENERAL,
    subTypeReferenceId = undefined,
}: ChannelDTO) => {
    const exampleChannelDTO = new ChannelDTO();
    exampleChannelDTO.id = id;
    exampleChannelDTO.name = name;
    exampleChannelDTO.description = description;
    exampleChannelDTO.topic = topic;
    exampleChannelDTO.isPublic = isPublic;
    exampleChannelDTO.isArchived = isArchived;
    exampleChannelDTO.isChannelModerator = isChannelModerator;
    exampleChannelDTO.hasChannelModerationRights = hasChannelModerationRights;
    exampleChannelDTO.creationDate = creationDate;
    exampleChannelDTO.lastMessageDate = lastMessageDate;
    exampleChannelDTO.lastReadDate = lastReadDate;
    exampleChannelDTO.numberOfMembers = numberOfMembers;
    exampleChannelDTO.creator = creator;
    exampleChannelDTO.isCreator = isCreator;
    exampleChannelDTO.isFavorite = isFavorite;
    exampleChannelDTO.isHidden = isHidden;
    exampleChannelDTO.isMuted = isMuted;
    exampleChannelDTO.isMember = isMember;
    exampleChannelDTO.isAnnouncementChannel = isAnnouncementChannel;
    exampleChannelDTO.isCourseWide = isCourseWide;
    exampleChannelDTO.unreadMessagesCount = unreadMessagesCount;
    exampleChannelDTO.tutorialGroupTitle = tutorialGroupTitle;
    exampleChannelDTO.tutorialGroupId = tutorialGroupId;
    exampleChannelDTO.subType = subType;
    exampleChannelDTO.subTypeReferenceId = subTypeReferenceId;

    return exampleChannelDTO;
};

export const generateExampleGroupChatDTO = ({
    id = 1,
    name = 'awesome-group',
    creationDate = dayjs(),
    lastMessageDate = dayjs(),
    lastReadDate = dayjs(),
    numberOfMembers = 2,
    creator = { id: 1, login: 'login', firstName: 'Kaddl', lastName: 'Garching' } as ConversationUserDTO,
    members = [
        { id: 1, login: 'login', firstName: 'Kaddl', lastName: 'Garching' } as ConversationUserDTO,
        { id: 2, login: 'login2', firstName: 'Kaddl2', lastName: 'Garching2' } as ConversationUserDTO,
    ],
    isCreator = true,
    isFavorite = false,
    isHidden = false,
    isMuted = false,
    isMember = true,
    unreadMessagesCount = 0,
}: GroupChatDTO) => {
    const exampleGroupChatDTO = new GroupChatDTO();
    exampleGroupChatDTO.id = id;
    exampleGroupChatDTO.name = name;
    exampleGroupChatDTO.creationDate = creationDate;
    exampleGroupChatDTO.lastMessageDate = lastMessageDate;
    exampleGroupChatDTO.lastReadDate = lastReadDate;
    exampleGroupChatDTO.numberOfMembers = numberOfMembers;
    exampleGroupChatDTO.creator = creator;
    exampleGroupChatDTO.isCreator = isCreator;
    exampleGroupChatDTO.isFavorite = isFavorite;
    exampleGroupChatDTO.isHidden = isHidden;
    exampleGroupChatDTO.isMuted = isMuted;
    exampleGroupChatDTO.members = members;
    exampleGroupChatDTO.isMember = isMember;
    exampleGroupChatDTO.unreadMessagesCount = unreadMessagesCount;

    return exampleGroupChatDTO;
};

export const generateOneToOneChatDTO = ({
    id = 1,
    creationDate = dayjs(),
    lastMessageDate = dayjs(),
    lastReadDate = dayjs(),
    numberOfMembers = 2,
    creator = { id: 1, login: 'login', firstName: 'Kaddl', lastName: 'Garching' } as ConversationUserDTO,
    members = [
        { id: 1, login: 'login', firstName: 'Kaddl', lastName: 'Garching' } as ConversationUserDTO,
        { id: 2, login: 'login2', firstName: 'Kaddl2', lastName: 'Garching2' } as ConversationUserDTO,
    ],
    isCreator = true,
    isFavorite = false,
    isHidden = false,
    isMuted = false,
    isMember = true,
    unreadMessagesCount = 0,
}: GroupChatDTO) => {
    const exampleOneToOneChatDTO = new OneToOneChatDTO();
    exampleOneToOneChatDTO.id = id;
    exampleOneToOneChatDTO.creationDate = creationDate;
    exampleOneToOneChatDTO.lastMessageDate = lastMessageDate;
    exampleOneToOneChatDTO.lastReadDate = lastReadDate;
    exampleOneToOneChatDTO.numberOfMembers = numberOfMembers;
    exampleOneToOneChatDTO.creator = creator;
    exampleOneToOneChatDTO.isCreator = isCreator;
    exampleOneToOneChatDTO.isFavorite = isFavorite;
    exampleOneToOneChatDTO.isHidden = isHidden;
    exampleOneToOneChatDTO.isMuted = isMuted;
    exampleOneToOneChatDTO.members = members;
    exampleOneToOneChatDTO.isMember = isMember;
    exampleOneToOneChatDTO.unreadMessagesCount = unreadMessagesCount;
    return exampleOneToOneChatDTO;
};
