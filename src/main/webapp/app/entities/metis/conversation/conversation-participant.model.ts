import dayjs from 'dayjs/esm';

import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class ConversationParticipant implements BaseEntity {
    public id?: number;
    public user: User;
    public lastRead?: dayjs.Dayjs;
    public unreadMessagesCount: number;
    public closed: boolean;
}
