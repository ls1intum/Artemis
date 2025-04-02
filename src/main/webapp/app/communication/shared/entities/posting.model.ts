import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { UserRole } from 'app/communication/metis.util';
import { Conversation } from 'app/communication/shared/entities/conversation/conversation.model';

// NOTE: this should be the same as on the server side to avoid issues.
export enum SavedPostStatus {
    IN_PROGRESS = 'IN_PROGRESS',
    COMPLETED = 'COMPLETED',
    ARCHIVED = 'ARCHIVED',
}

export function toSavedPostStatus(value: string): SavedPostStatus | undefined {
    const upper = value.toUpperCase();
    return Object.values(SavedPostStatus).find((status) => status === upper);
}

// NOTE: this should be the same as on the server side to avoid issues.
export enum PostingType {
    POST = 'POST',
    ANSWER = 'ANSWER',
}

export abstract class Posting implements BaseEntity {
    public id?: number;
    public referencePostId?: number;
    public author?: User;
    public authorRole?: UserRole;
    public creationDate?: dayjs.Dayjs;
    public updatedDate?: dayjs.Dayjs;
    public content?: string;
    public isSaved?: boolean;
    public savedPostStatus?: SavedPostStatus;
    public postingType?: PostingType;
    public reactions?: Reaction[];
    public hasForwardedMessages?: boolean = false;
    public isConsecutive?: boolean = false;
    public conversation?: Conversation;
}
