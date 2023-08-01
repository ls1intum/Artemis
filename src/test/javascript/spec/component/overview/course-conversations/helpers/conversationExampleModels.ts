import dayjs from 'dayjs/esm';
import { ChannelDTO, ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { GroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
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
    isMember = true,
    isAnnouncementChannel = false,
    isCourseWide = false,
    unreadMessagesCount = 0,
    tutorialGroupTitle = undefined,
    tutorialGroupId = undefined,
    subType = ChannelSubType.GENERAL,
    subTypeReferenceId = undefined,
}: ChannelDTO) => {
    const exampleChannelDto = new ChannelDTO();
    exampleChannelDto.id = id;
    exampleChannelDto.name = name;
    exampleChannelDto.description = description;
    exampleChannelDto.topic = topic;
    exampleChannelDto.isPublic = isPublic;
    exampleChannelDto.isArchived = isArchived;
    exampleChannelDto.isChannelModerator = isChannelModerator;
    exampleChannelDto.hasChannelModerationRights = hasChannelModerationRights;
    exampleChannelDto.creationDate = creationDate;
    exampleChannelDto.lastMessageDate = lastMessageDate;
    exampleChannelDto.lastReadDate = lastReadDate;
    exampleChannelDto.numberOfMembers = numberOfMembers;
    exampleChannelDto.creator = creator;
    exampleChannelDto.isCreator = isCreator;
    exampleChannelDto.isFavorite = isFavorite;
    exampleChannelDto.isHidden = isHidden;
    exampleChannelDto.isMember = isMember;
    exampleChannelDto.isAnnouncementChannel = isAnnouncementChannel;
    exampleChannelDto.isCourseWide = isCourseWide;
    exampleChannelDto.unreadMessagesCount = unreadMessagesCount;
    exampleChannelDto.tutorialGroupTitle = tutorialGroupTitle;
    exampleChannelDto.tutorialGroupId = tutorialGroupId;
    exampleChannelDto.subType = subType;
    exampleChannelDto.subTypeReferenceId = subTypeReferenceId;

    return exampleChannelDto;
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
    isMember = true,
    unreadMessagesCount = 0,
}: GroupChatDto) => {
    const exampleGroupChatDTO = new GroupChatDto();
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
    isMember = true,
    unreadMessagesCount = 0,
}: GroupChatDto) => {
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
    exampleOneToOneChatDTO.members = members;
    exampleOneToOneChatDTO.isMember = isMember;
    exampleOneToOneChatDTO.unreadMessagesCount = unreadMessagesCount;
    return exampleOneToOneChatDTO;
};
