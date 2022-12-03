import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-details.model';

export class Conversation implements BaseEntity {
    public id?: number;
    public conversationParticipants?: ConversationParticipant[];
    public course: Course;
    public creationDate?: dayjs.Dayjs;
    public lastMessageDate?: dayjs.Dayjs;
}
