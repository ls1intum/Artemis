import { User } from 'app/account/user/user.model';
import { BaseEntity } from 'app/foundation/model/base-entity';
import dayjs from 'dayjs/esm';

export interface ConversationParticipant extends BaseEntity {
    id?: number;
    user: User;
    lastRead?: dayjs.Dayjs;
    unreadMessagesCount: number;
    closed: boolean;
}
