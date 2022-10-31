import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-participant.model';
import { ConversationType } from 'app/shared/metis/metis.util';

export class Conversation implements BaseEntity {
    public id?: number;
    public conversationParticipants?: ConversationParticipant[];
    public course: Course;
    public creationDate?: dayjs.Dayjs;
    public lastMessageDate?: dayjs.Dayjs;

    // === START ADDED BY STEFAN ===

    /**
     * Note: Only for type channel
     */
    public name?: string | undefined; // max 20 characters
    /**
     * Note: Only for type channel
     */
    public description?: string | undefined; // max 200 characters
    /**
     * Note: Only for type channel
     */
    public isPublic?: boolean;

    public type?: ConversationType;

    // === END ADDED BY STEFAN ===
}
