import { Conversation, ConversationType } from 'app/entities/metis/conversation/conversation.model';

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
