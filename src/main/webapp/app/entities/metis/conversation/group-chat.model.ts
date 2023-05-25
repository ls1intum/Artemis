import { Conversation, ConversationDto, ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';

export class GroupChat extends Conversation {
    public name?: string; // max 20 characters

    constructor() {
        super(ConversationType.GROUP_CHAT);
    }
}
export class GroupChatDto extends ConversationDto {
    public members?: ConversationUserDTO[];
    public name?: string;

    constructor() {
        super(ConversationType.GROUP_CHAT);
    }
}
export function isGroupChatDto(conversation: ConversationDto): conversation is GroupChatDto {
    return conversation.type === ConversationType.GROUP_CHAT;
}

export function getAsGroupChatDto(conversation: ConversationDto | undefined): GroupChatDto | undefined {
    if (!conversation) {
        return undefined;
    }
    return isGroupChatDto(conversation) ? conversation : undefined;
}
