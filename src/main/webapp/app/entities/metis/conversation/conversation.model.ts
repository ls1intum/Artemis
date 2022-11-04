import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-participant.model';

// IMPORTANT NOTICE: The following strings have to be consistent with
// the ones defined in Conversation.java
export enum ConversationType {
    GROUP_CHAT = 'groupChat',
    CHANNEL = 'channel',
}

export const MAX_MEMBERS_IN_DIRECT_CONVERSATION = 6;

export abstract class Conversation implements BaseEntity {
    public id?: number;
    public conversationParticipants?: ConversationParticipant[];
    public course: Course;
    public creationDate?: dayjs.Dayjs;
    public lastMessageDate?: dayjs.Dayjs;
    public type?: ConversationType;

    // calculated property
    public numberOfMembers?: number;

    protected constructor(type: ConversationType) {
        this.type = type;
    }
}
