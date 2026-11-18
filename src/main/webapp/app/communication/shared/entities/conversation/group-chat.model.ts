import { Conversation, ConversationDTO, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';

export class GroupChat extends Conversation {
    public name?: string; // max 20 characters

    constructor() {
        super(ConversationType.GROUP_CHAT);
    }
}
export class GroupChatDTO extends ConversationDTO {
    public members?: ConversationUserDTO[];
    public name?: string;

    constructor() {
        super(ConversationType.GROUP_CHAT);
    }
}
export function isGroupChatDTO(conversation: ConversationDTO): conversation is GroupChatDTO {
    return conversation.type === ConversationType.GROUP_CHAT;
}

export function getAsGroupChatDTO(conversation: ConversationDTO | undefined): GroupChatDTO | undefined {
    if (!conversation) {
        return undefined;
    }
    return isGroupChatDTO(conversation) ? conversation : undefined;
}
