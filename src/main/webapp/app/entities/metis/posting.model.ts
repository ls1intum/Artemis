import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { Moment } from 'moment';
import { Reaction } from 'app/entities/metis/reaction.model';

export abstract class Posting implements BaseEntity {
    public id?: number;
    public author?: User;
    public creationDate?: Moment;
    public content?: string;
    public reactions?: Reaction[];
}
