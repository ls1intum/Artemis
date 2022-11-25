import dayjs from 'dayjs/esm';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { GroupChatDto } from 'app/entities/metis/conversation/group-chat.model';

export const generateExampleChannelDTO = ({
    id = 1,
    name = 'general',
    description = 'general channel',
    topic = 'general',
    isPublic = true,
    isArchived = false,
    isAdmin = true,
    hasChannelAdminRights = true,
    creationDate = dayjs(),
    lastMessageDate = dayjs(),
    lastReadDate = dayjs(),
    numberOfMembers = 10,
    creator = { id: 1, login: 'login', firstName: 'Kaddl', lastName: 'Garching' } as ConversationUserDTO,
    isCreator = true,
    isFavorite = false,
    isHidden = false,
}: ChannelDTO) => {
    const exampleChannelDto = new ChannelDTO();
    exampleChannelDto.id = id;
    exampleChannelDto.name = name;
    exampleChannelDto.description = description;
    exampleChannelDto.topic = topic;
    exampleChannelDto.isPublic = isPublic;
    exampleChannelDto.isArchived = isArchived;
    exampleChannelDto.isAdmin = isAdmin;
    exampleChannelDto.hasChannelAdminRights = hasChannelAdminRights;
    exampleChannelDto.creationDate = creationDate;
    exampleChannelDto.lastMessageDate = lastMessageDate;
    exampleChannelDto.lastReadDate = lastReadDate;
    exampleChannelDto.numberOfMembers = numberOfMembers;
    exampleChannelDto.creator = creator;
    exampleChannelDto.isCreator = isCreator;
    exampleChannelDto.isFavorite = isFavorite;
    exampleChannelDto.isHidden = isHidden;

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

    return exampleGroupChatDTO;
};
