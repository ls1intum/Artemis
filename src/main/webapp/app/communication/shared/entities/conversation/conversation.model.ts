import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ConversationParticipant } from 'app/communication/shared/entities/conversation/conversation-participant.model';
import { User } from 'app/core/user/user.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';

// IMPORTANT NOTICE: The following strings have to be consistent with
// the ones defined in Conversation.java and ConversationDTO.java
export enum ConversationType {
    GROUP_CHAT = 'groupChat',
    CHANNEL = 'channel',
    ONE_TO_ONE = 'oneToOneChat',
}
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
    public title?: string;

    protected constructor(type: ConversationType) {
        this.type = type;
    }
}

/**
 * DTO
 */
export abstract class ConversationDTO {
    public type?: ConversationType;
    public id?: number;
    public creationDate?: dayjs.Dayjs;
    public lastMessageDate?: dayjs.Dayjs;
    public lastReadDate?: dayjs.Dayjs;
    public unreadMessagesCount?: number;
    public isMember?: boolean;
    public numberOfMembers?: number;
    public creator?: ConversationUserDTO;
    public isCreator?: boolean;
    public isFavorite?: boolean;
    public isHidden?: boolean;
    public isMuted?: boolean;
    // ?
    public isMarkedAsUnread?: boolean;
    public hasUnreadMessage?: boolean;

    protected constructor(type: ConversationType) {
        this.type = type;
    }
}

/**
 * Checks if a notification, due to this conversation, should notify its recipients.
 * @param conversation a conversation
 */
export function shouldNotifyRecipient(conversation: ConversationDTO): boolean {
    return !conversation.isMuted && !conversation.isHidden;
}
