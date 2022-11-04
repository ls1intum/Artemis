import { Conversation, ConversationType } from 'app/entities/metis/conversation/conversation.model';

export class Channel extends Conversation {
    public name?: string | undefined; // max 20 characters
    public description?: string | undefined; // max 200 characters
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
