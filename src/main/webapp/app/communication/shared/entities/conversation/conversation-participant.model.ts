import { User } from 'app/account/user/user.model';
import { BaseEntity } from 'app/foundation/model/base-entity';
import dayjs from 'dayjs/esm';

export class ConversationParticipant implements BaseEntity {
    public id?: number;
    public user: User;
    public lastRead?: dayjs.Dayjs;
    public unreadMessagesCount: number;
    public closed: boolean;
}
