import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { Reaction } from 'app/entities/metis/reaction.model';
import { UserRole } from 'app/shared/metis/metis.util';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

export enum SavedPostStatus {
    PROGRESS = 'progress',
    COMPLETED = 'completed',
    ARCHIVED = 'archived',
}

export enum PostingType {
    POST = 'post',
    ANSWER = 'answer',
}

export abstract class Posting implements BaseEntity {
    public id?: number;
    public author?: User;
    public authorRole?: UserRole;
    public creationDate?: dayjs.Dayjs;
    public updatedDate?: dayjs.Dayjs;
    public content?: string;
    public isSaved?: boolean;
    public savedPostStatus?: string;
    public postingType?: string;
    public reactions?: Reaction[];
    public conversation?: Conversation;
}
