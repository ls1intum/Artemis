import { Conversation, ConversationDto, ConversationType } from 'app/entities/metis/conversation/conversation.model';

/**
 * Entity
 */
export class Channel extends Conversation {
    public name?: string; // max 20 characters
    public description?: string; // max 200 characters
    public isPublic?: boolean;

    constructor() {
        super(ConversationType.CHANNEL);
    }
}

export function isChannel(conversation: Conversation): conversation is Channel {
    return conversation.type === ConversationType.CHANNEL;
}

export function getAsChannel(conversation: Conversation): Channel | undefined {
    return isChannel(conversation) ? conversation : undefined;
}

/**
 * DTO
 */
export class ChannelDTO extends ConversationDto {
    public name?: string;
    public description?: string;
    public isPublic?: boolean;

    constructor() {
        super(ConversationType.CHANNEL);
    }
}

export function isChannelDto(conversation: ConversationDto): conversation is ChannelDTO {
    return conversation.type === ConversationType.CHANNEL;
}

export function getAsChannelDto(conversation: ConversationDto): ChannelDTO | undefined {
    return isChannelDto(conversation) ? conversation : undefined;
}
