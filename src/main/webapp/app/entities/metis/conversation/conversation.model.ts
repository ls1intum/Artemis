import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-participant.model';
import { User } from 'app/core/user/user.model';

// IMPORTANT NOTICE: The following strings have to be consistent with
// the ones defined in Conversation.java and ConversationDTO.java
export enum ConversationType {
    GROUP_CHAT = 'groupChat',
    CHANNEL = 'channel',
}

export const MAX_MEMBERS_IN_DIRECT_CONVERSATION = 6;

/**
 * Entity
 */
export abstract class Conversation implements BaseEntity {
    public type?: ConversationType;
    public id?: number;
    public conversationParticipants?: ConversationParticipant[];
    public course?: Course;
    public creator?: User;
    public creationDate?: dayjs.Dayjs;
    public lastMessageDate?: dayjs.Dayjs;

    protected constructor(type: ConversationType) {
        this.type = type;
    }
}

/**
 * DTO
 */
export abstract class ConversationDto {
    public type?: ConversationType;
    public id?: number;
    public creationDate?: dayjs.Dayjs;
    public lastMessageDate?: dayjs.Dayjs;
    public isMember?: boolean;
    public numberOfMembers?: number;
    public creator?: User;
    public isCreator?: boolean;

    protected constructor(type: ConversationType) {
        this.type = type;
    }
}
