import { Conversation, ConversationDTO, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';

export class OneToOneChat extends Conversation {
    constructor() {
        super(ConversationType.ONE_TO_ONE);
    }
}
export class OneToOneChatDTO extends ConversationDTO {
    constructor() {
        super(ConversationType.ONE_TO_ONE);
    }
    public members?: ConversationUserDTO[];
}
export function isOneToOneChatDTO(conversation: ConversationDTO): conversation is OneToOneChatDTO {
    return conversation.type === ConversationType.ONE_TO_ONE;
}

export function getAsOneToOneChatDTO(conversation: ConversationDTO | undefined): OneToOneChatDTO | undefined {
    if (!conversation) {
        return undefined;
    }
    return isOneToOneChatDTO(conversation) ? conversation : undefined;
}
