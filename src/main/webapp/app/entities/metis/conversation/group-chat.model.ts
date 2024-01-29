import { Conversation, ConversationDTO, ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';

export class GroupChat extends Conversation {
    public name?: string; // max 20 characters

    constructor() {
        super(ConversationType.GROUP_CHAT);
    }
}
export class GroupChatDto extends ConversationDTO {
    public members?: ConversationUserDTO[];
    public name?: string;

    constructor() {
        super(ConversationType.GROUP_CHAT);
    }
}
export function isGroupChatDto(conversation: ConversationDTO): conversation is GroupChatDto {
    return conversation.type === ConversationType.GROUP_CHAT;
}

export function getAsGroupChatDto(conversation: ConversationDTO | undefined): GroupChatDto | undefined {
    if (!conversation) {
        return undefined;
    }
    return isGroupChatDto(conversation) ? conversation : undefined;
}
