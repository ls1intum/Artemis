import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';

export class UserChatSession implements BaseEntity {
    public id?: number;
    public user: User;
    public lastRead?: dayjs.Dayjs;
    public archived: boolean;
    public deleted: boolean;
}
