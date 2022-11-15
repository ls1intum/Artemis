import { Conversation, ConversationDto, ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';

export class GroupChat extends Conversation {
    constructor() {
        super(ConversationType.GROUP_CHAT);
    }
}

export function isGroupChat(conversation: Conversation): conversation is GroupChat {
    return conversation.type === ConversationType.GROUP_CHAT;
}

export function getAsGroupChat(conversation: Conversation): GroupChat | undefined {
    return isGroupChat(conversation) ? conversation : undefined;
}

export class GroupChatDto extends ConversationDto {
    public members?: ConversationUserDTO[];
    constructor() {
        super(ConversationType.GROUP_CHAT);
    }
}
export function isGroupChatDto(conversation: ConversationDto): conversation is GroupChatDto {
    return conversation.type === ConversationType.GROUP_CHAT;
}

export function getAsGroupChatDto(conversation: ConversationDto): GroupChatDto | undefined {
    return isGroupChatDto(conversation) ? conversation : undefined;
}
