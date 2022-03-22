import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { UserChatSession } from 'app/entities/metis/chat.session/user-chat-session.model';

export class ChatSession implements BaseEntity {
    public id?: number;
    public userChatSessions?: UserChatSession[];
    public course: Course;
    public creationDate?: dayjs.Dayjs;
    public lastMessageDate?: dayjs.Dayjs;
}
