import dayjs from 'dayjs/esm';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';

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
    isFavorite = true,
    isHidden = true,
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
