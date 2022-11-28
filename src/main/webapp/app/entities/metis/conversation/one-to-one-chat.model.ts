import { Conversation, ConversationDto, ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';

export class OneToOneChat extends Conversation {
    constructor() {
        super(ConversationType.ONE_TO_ONE);
    }
}

export function isOneToOneChat(conversation: Conversation): conversation is OneToOneChat {
    return conversation.type === ConversationType.ONE_TO_ONE;
}

export function getAsOneToOneChat(conversation: Conversation | undefined): OneToOneChat | undefined {
    if (!conversation) {
        return undefined;
    }
    return isOneToOneChat(conversation) ? conversation : undefined;
}

export class OneToOneChatDTO extends ConversationDto {
    constructor() {
        super(ConversationType.ONE_TO_ONE);
    }
    public members?: ConversationUserDTO[];
}
export function isOneToOneChatDto(conversation: ConversationDto): conversation is OneToOneChatDTO {
    return conversation.type === ConversationType.ONE_TO_ONE;
}

export function getAsOneToOneChatDto(conversation: ConversationDto | undefined): OneToOneChatDTO | undefined {
    if (!conversation) {
        return undefined;
    }
    return isOneToOneChatDto(conversation) ? conversation : undefined;
}
