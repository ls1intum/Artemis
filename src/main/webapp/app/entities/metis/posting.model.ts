import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { Reaction } from 'app/entities/metis/reaction.model';
import { UserRole } from 'app/shared/metis/metis.util';

export abstract class Posting implements BaseEntity {
    public id?: number;
    public author?: User;
    public authorRole?: UserRole;
    public creationDate?: dayjs.Dayjs;
    public content?: string;
    public reactions?: Reaction[];
}
